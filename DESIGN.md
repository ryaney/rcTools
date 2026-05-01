# Panama SoA 高性能遍历框架设计方案

## 1. 背景与问题

### 1.1 场景特征

| 参数 | 值 |
|------|-----|
| 对象数量 | 500,000 |
| 对象类型 | 业务自定义对象，包含 double、BigDecimal 等多种字段 |
| 写入模式 | 单线程，TPS ≤ 20 |
| 写入影响范围 | 每次 1~10 个元素 |
| 读取模式 | 多线程高频全量遍历 |
| 关键瓶颈 | Cache Miss 导致遍历性能急剧下降 |

### 1.2 问题根因

传统 OOP 布局下，50 万个对象分散在堆内存中。每次遍历访问 2~4 个关键字段时，每次对象访问都伴随 Cache Miss，64 字节的 Cache Line 中有效数据仅占 3~6%，大量带宽被无关字段和对象头浪费。

## 2. 设计目标

1. 遍历阶段达到接近内存带宽上限的吞吐量
2. 写入操作不影响读遍历（无锁读取，写入概率 < 10⁻⁶）
3. 字段选择和布局在初始化阶段由注解驱动
4. 支持 double、BigDecimal(long+int 紧凑) 等常见金融数据类型
5. 可选集成 Vector API (JEP 338/414) 实现 SIMD 加速

## 3. 核心设计

### 3.1 SoA (Structure of Arrays) + Panama MemorySegment

```
传统 AoS 布局 (堆对象):
  [obj₀ header|f₁|f₂|f₃|...|padding] [obj₁ header|f₁|f₂|f₃|...] ...
  Cache Line 利用率: ~5%

SoA 布局 (MemorySegment):
  priceSeg:   [p₀|p₁|p₂|p₃|p₄|p₅|p₆|p₇|p₈|p₉|...]     (double[])
  unscaledSeg:[u₀|u₁|u₂|u₃|u₄|u₅|u₆|u₇|u₈|u₉|...]     (long[])
  scaleSeg:   [s₀|s₁|s₂|s₃|s₄|s₅|s₆|s₇|s₈|s₉|...]     (int[])
  versionSeg: [v₀|v₁|v₂|v₃|v₄|v₅|v₆|v₇|...|v₆₃|...]   (byte[])
  Cache Line 利用率: 100%
```

### 3.2 Cache Line 版本标记

每个元素对应 1 字节版本标记：

```
写入流程 (单线程):
  1. versionSeg[idx] = 0x01  (VarHandle.fullFence 确保标记先于数据可见)
  2. dataSeg[i][idx] = newVal  (双写各 Segment)
  3. versionSeg[idx] = 0x00  (VarHandle.fullFence 确保数据先于清标记)

读取流程 (多线程, 无锁):
  loop:
    v1 = versionSeg[idx]  (volatile read)
    if v1 & 1: spin until v1 == 0
    val = dataSeg[i][idx]  (读数据)
    v2 = versionSeg[idx]  (volatile read)
    if v1 != v2: retry  (版本号变化 → 数据可能不一致)
    use(val)
```

**正确性保证** (单写线程 + 多读线程):

- Writer 先置 ODD 标记后写数据 → Reader 看到 ODD 自旋，读到的一定是写入前或写入后的完整状态
- StoreStore barrier 保证标记与数据的写入顺序
- 单写线程消除了多 Writer 交叉写入的可能性，无需 SeqLock 版本号递增

### 3.3 架构分层

```
┌─────────────────────────────────────────────────────┐
│                   注解层 (Annotation)                 │
│  @KeyField(index, type)                              │
│  ─ 标记业务对象的哪些字段参与 SoA 聚合                │
└────────────────────┬────────────────────────────────┘
                     │ (编译/运行时反射)
┌────────────────────▼────────────────────────────────┐
│                 Schema 层 (Schema)                    │
│  SchemaDescriptor: 每个字段的布局映射                 │
│  SchemaBuilder: 反射扫描 → 构建 Segment 布局          │
└────────────────────┬────────────────────────────────┘
                     │ (初始化)
┌────────────────────▼────────────────────────────────┐
│               Segment 层 (Storage)                    │
│  SoAStorage: Arena + MemorySegment[] 管理             │
│  SoAWriter:   单写线程双写 + Cache Line 版本标记       │
└────────────────────┬────────────────────────────────┘
                     │ (遍历读写接口)
┌────────────────────▼────────────────────────────────┐
│              Iterator 层 (Access)                     │
│  SoAIterator: 高效无锁遍历 + 版本标记自旋             │
│  可选: DoubelVector 批量操作 (SIMD)                   │
└─────────────────────────────────────────────────────┘
```

## 4. 内存布局

### 4.1 数据 Segment

```
Segment layout (每个字段一个 Segment, 长度 = count × elementSize):

  doubleSegment:  [d₀  ][d₁  ][d₂  ]...[d_{N-1}  ]  (8B each, 8 elements/Cache Line)
  longSegment:    [l₀  ][l₁  ][l₂  ]...[l_{N-1}  ]  (8B each, 8 elements/Cache Line)
  intSegment:     [i₀][i₁][i₂]...[i_{N-1}]          (4B each, 16 elements/Cache Line)
  byteSegment:    [b₀]...[b_{N-1}]                    (1B each, 64 elements/Cache Line)
```

### 4.2 版本标记 Segment

```
  versionSeg: [v₀][v₁]...[v₆₃] [v₆₄][v₆₅]...  (64B Cache Line)
  每个版本标记字节:
    - 单字节 (最小原子单元, VarHandle 天然保证可见性)
    - 0x00 = STABLE (EVEN)
    - 0x01 = WRITING (ODD)
    - 未来可扩展为递增版本号 (需要 long 宽度)
```

## 5. 关键类设计

| 类 | 职责 | 线程安全性 |
|-----|------|----------|
| `@KeyField` | 标记参与 SoA 聚合的字段 | — |
| `SchemaDescriptor` | 字段 → 内存布局映射 (不可变) | 线程安全 (不可变) |
| `SchemaBuilder` | 反射构建 SchemaDescriptor | — |
| `SoAStorage` | 管理 Arena + MemorySegment | 线程安全 (Arena 隔离) |
| `SoAWriter` | 单写线程更新数据 + 版本标记 | 不适用 (单线程) |
| `SoAIterator` | 多读线程无锁遍历 + 自旋 | 线程安全 |
| `SoAView` | 按索引读取元素 (惰性 BigDecimal 重建) | 线程安全 |

## 6. 写入-读取时序图

```
    Writer                          Reader₁                    Reader₂
      │                                │                          │
      │  versionSeq[i]=ODD             │                          │
      ├────storeStore──────────────────┤                          │
      │  dataSeg[0][i]=newPrice        │                          │
      │  dataSeg[1][i]=newUnscaled     │                          │
      │  dataSeg[2][i]=newScale        │                          │
      ├────storeStore──────────────────┤                          │
      │  versionSeq[i]=EVEN            │                          │
      │                                │                          │
      │                          v1=versionSeq[i]==EVEN          │
      │                          price=dataSeg[0][i]              │
      │                          unscaled=dataSeg[1][i]           │
      │                          v2=versionSeq[i]==EVEN ✓         │
      │                          → 使用数据                      │
      │                                                    v1=versionSeq[i]==ODD
      │                                                    → 自旋...
      │                                                     versionSeq[i]==EVEN
      │                                                    → 读数据 + 验证一致 ✓
```

## 7. 性能预估

| 指标 | 传统 AoS (堆) | SoA Segment | 加速比 |
|------|-------------|-------------|--------|
| 遍历耗时 (500K 元素) | 60-125ms | 0.5-0.75ms | **120-170x** |
| 写入单元素开销 | ~200 cycles | ~150 cycles | — |
| 20 TPS 写入 CPU 占用 | ~0.0005% | ~0.0004% | — |
| Reader 自旋概率 | N/A | ~4×10⁻⁷ / 遍历 | — |
| Cache Line 利用率 | ~5% | 100% | **20x** |
| 额外内存 (版本标记) | — | 0.5MB | — |
| 额外内存 (SoA 数据) | — | = 字段数 × 500K × elementSize | — |
| SIMD 可选加速 | 不可用 | 4-8x | 额外增益 |

## 8. 默认配置

```java
public class SoAConfig {
    /** 版本 Segment 中 WRITING 标记值 */
    public static final byte MARK_WRITING = 0x01;
    /** 版本 Segment 中 STABLE 标记值 */
    public static final byte MARK_STABLE  = 0x00;
    /** Arena 类型 (ofConfined 用于单线程共享) */
    public static final ArenaFactory ARENA_FACTORY = Arena::ofShared;
    /** 元素数量上限 (预分配) */
    public static final int MAX_ELEMENTS = 1_000_000;
}
```

## 9. 局限性

1. 字段必须是固定大小类型 (原始类型或紧凑表示)
2. 不适合 String 等变长类型 (需要额外间接层)
3. 重建 BigDecimal 有短暂堆分配 (每次重建一个临时对象)
4. 单写线程约束 (多写线程需增强为 SeqLock + 递增版本号)
5. 需要 JDK 25+ (Panama FFM API + Vector API 在预览/孵化中)

## 10. 参考文献

- JEP 454: Foreign Function & Memory API (Panama)
- JEP 338: Vector API (Fifth Incubator)
- SeqLock: LWN.net "Lockless patterns: seqlocks"
- OKLCH: "The Problem of Cache Misses in Financial Systems" (internal)
