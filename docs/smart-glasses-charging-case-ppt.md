# 智能眼镜无线充电盒仓 MCU 技术方案 PPT

## Slide 1 - 项目标题

**智能眼镜无线充电盒仓 MCU 技术方案**

副标题：

- 2000mAh 充电盒
- 盒子 Master，眼镜 Slave
- 充电检测、状态指示、盒子 MCU OTA

演讲要点：

- 本方案聚焦充电盒 MCU 的硬件、固件和通信协议设计。
- 重点解决“盒子主动管理眼镜”和“准确判断眼镜是否正在充电”。

---

## Slide 2 - 产品目标

**核心目标**

- 2000mAh 单节锂电池供电。
- 支持 USB-C 充电，可选 Qi 无线充电。
- 支持给智能眼镜充电。
- 支持盒子充电状态和电量指示。
- 支持检测眼镜入仓、充电中、满电、异常。
- 支持眼镜为盒子 MCU 提供 OTA 固件。

演讲要点：

- 盒子不只是电池仓，还承担电源管理、通信主控和升级控制功能。

---

## Slide 3 - 系统角色定义

**主从关系**

```text
充电盒 = Master
智能眼镜 = Slave
```

**充电盒负责**

- 主动发起通信。
- 检测眼镜入仓。
- 控制充电输出。
- 轮询眼镜状态。
- 控制 LED 指示。
- 主动拉取 OTA 固件。

**眼镜负责**

- 响应盒子命令。
- 上报电量、温度、充电状态。
- 为盒子 OTA 提供固件数据。

演讲要点：

- 盒子作为 Master，更符合入仓后由盒子统一管理电源和状态的产品逻辑。

---

## Slide 4 - 系统硬件架构

```text
USB-C / Qi
   |
   v
Charger / PMIC ---> 2000mAh Battery
   |
   +--> 3.3V / 1.8V ---> MCU
   |
   +--> Load Switch L --> Current Sense L --> Left Glasses
   |
   +--> Load Switch R --> Current Sense R --> Right Glasses

MCU:
- UART to glasses
- I2C fuel gauge
- ADC battery/current/NTC
- GPIO hall/present/LED
```

演讲要点：

- 推荐左右眼镜独立充电输出和独立电流检测。
- Fuel Gauge 比纯 ADC 电压估算更适合量产电量显示。

---

## Slide 5 - 推荐硬件模块

| 模块 | 推荐设计 |
| --- | --- |
| MCU | 低功耗 Cortex-M0+/M3，128KB~256KB Flash |
| 电池 | 2000mAh 单节锂电池 + NTC + 保护 |
| 输入 | USB-C 5V，选配 Qi 5W 无线接收 |
| 电量 | Fuel Gauge，I2C 读取 SOC |
| 眼镜输出 | 左右独立 Load Switch + 限流 + 故障检测 |
| 通信 | Pogo Pin UART |
| 指示 | RGB LED 或 4 颗白灯 |

演讲要点：

- 标准方案强调可靠性和量产一致性。
- 成本敏感版本可去掉 Fuel Gauge，改用 ADC 粗略估算。

---

## Slide 6 - 触点与通信方式

**推荐触点**

```text
Pin1: VCHG_L
Pin2: GND_L
Pin3: VCHG_R
Pin4: GND_R
Pin5: BOX_TX -> GLASSES_RX
Pin6: BOX_RX <- GLASSES_TX
```

**UART 参数**

- 115200bps 默认。
- OTA 可切换到 921600bps。
- 8N1。
- 无硬件流控。
- 协议层 ACK/NACK。

演讲要点：

- 相比 BLE，触点 UART 成本低、稳定、便于 OTA 和产测。

---

## Slide 7 - 盒子 MCU 固件架构

```text
Application
  +-- Power Manager
  +-- Charge Manager
  +-- Glasses Detect Manager
  +-- Communication Manager
  +-- OTA Manager
  +-- LED Manager
  +-- Fault Manager
  +-- Low Power Manager

Bootloader
  +-- Image Verify
  +-- Image Apply
  +-- Rollback
```

演讲要点：

- Application 负责业务。
- Bootloader 负责升级安全和回滚。
- OTA 不应直接覆盖当前运行 App。

---

## Slide 8 - 眼镜是否正在充电：检测思路

**采用双判断**

```text
盒子侧电流检测
        +
眼镜侧充电状态上报
        =
最终充电状态
```

**为什么需要双判断**

- 只看电流：可能是待机耗电，不一定在充电。
- 只看眼镜上报：触点或输出异常时不可靠。
- 两者结合：可区分充电中、满电、触点异常、故障。

演讲要点：

- 这是本方案最关键的可靠性设计点。

---

## Slide 9 - 充电状态判断规则

| 条件 | 判断 |
| --- | --- |
| 未检测到入仓 | 未入仓 |
| 电流 >30mA 持续 3s，眼镜上报 precharge/fast/taper | 正在充电 |
| 电流 <30mA，眼镜上报 full | 已充满 |
| 输出开启但电流 <2mA，眼镜无响应 | 触点异常 |
| Load Switch 故障或眼镜 fault | 充电故障 |

**状态枚举**

```c
NOT_PRESENT
PRESENT_NOT_CHARGING
CHARGING
FULL
CHARGE_FAULT
CONTACT_BAD
```

演讲要点：

- 每个状态都能对应到 LED 和产测判定。

---

## Slide 10 - 协议帧格式

```text
+--------+-----+-----+-----+-----+-----+------+---------+--------+
| SOF    | Ver | Seq | Src | Dst | Cmd | Len  | Payload | CRC16  |
+--------+-----+-----+-----+-----+-----+------+---------+--------+
| A5 5A  | 1B  | 1B  | 1B  | 1B  | 1B  | 2B   | N bytes | 2B     |
+--------+-----+-----+-----+-----+-----+------+---------+--------+
```

**地址定义**

- 0x00：充电盒。
- 0x01：左眼镜。
- 0x02：右眼镜。
- 0x03：整副眼镜。
- 0xFF：广播。

演讲要点：

- Src/Dst 地址字段方便左右眼镜扩展。
- CRC16 保证触点通信可靠性。

---

## Slide 11 - 核心命令

**基础命令**

- PING
- GET_INFO
- GET_STATUS
- SET_MODE
- SLEEP
- ACK / NACK

**充电命令**

- CHARGE_ENABLE
- CHARGE_DISABLE
- SET_CHARGE_CURRENT
- GET_BATTERY
- GET_CHARGE_STATE
- CHARGE_STATE_RESP

**盒子通知**

- BOX_STATUS_NOTIFY
- BOX_CHARGE_NOTIFY
- LED_STATE_NOTIFY

演讲要点：

- 命令遵循“盒子主动请求，眼镜被动响应”的模型。

---

## Slide 12 - 眼镜充电状态 Payload

```c
typedef struct {
    uint8_t side;
    uint8_t vchg_present;
    uint8_t charger_state;
    uint8_t battery_soc;
    uint16_t battery_mv;
    uint16_t charge_current_ma;
    int16_t temperature;
    uint16_t fault_flags;
} glasses_charge_state_t;
```

**charger_state**

- idle
- precharge
- fast_charge
- taper
- full
- fault

演讲要点：

- 如果眼镜没有电流检测，`charge_current_ma` 可填 `0xFFFF`。

---

## Slide 13 - 盒子 Master 轮询流程

```text
眼镜入仓
  |
开启通信/充电电源
  |
等待 50~200ms
  |
PING
  |
GET_INFO
  |
GET_STATUS
  |
GET_CHARGE_STATE
  |
综合判断状态
  |
更新 LED 与充电策略
```

**轮询周期**

- 刚入仓：500ms。
- 正在充电：2s~5s。
- 已充满：10s~30s。
- 盒盖关闭稳定：30s~60s。

演讲要点：

- 轮询周期随状态变化，兼顾响应速度和低功耗。

---

## Slide 14 - 盒子 MCU OTA：Master Pull

**OTA 角色**

- 盒子 Master 主动拉取固件。
- 眼镜 Slave 保存并提供盒子固件数据。
- 盒子本地写入、校验、切换和重启。

```text
Box -> Glasses: OTA_CHECK
Glasses -> Box: version/size/crc/hash
Box -> Glasses: OTA_ENTER
Box -> Glasses: OTA_GET_BLOCK offset
Glasses -> Box: OTA_BLOCK data
Box: verify image
Box -> Glasses: OTA_APPLY
Box: reboot
```

演讲要点：

- Master Pull 保持主从关系一致，不让眼镜主动抢占通信节奏。

---

## Slide 15 - OTA 安全策略

| 风险 | 策略 |
| --- | --- |
| 固件损坏 | CRC32 + SHA256 |
| 非法固件 | 量产加入签名校验 |
| 低电升级失败 | SOC <30% 拒绝 OTA |
| 通信丢包 | block 重传 |
| 新固件启动失败 | Bootloader 回滚 |
| 升级中断 | Metadata 标记升级状态 |

演讲要点：

- OTA 的重点不是能升级，而是升级失败后设备仍可恢复。

---

## Slide 16 - LED 状态指示

| 状态 | RGB LED |
| --- | --- |
| 盒子充电中 | 橙色慢闪 |
| 盒子满电 | 绿色常亮后熄灭 |
| 盒子低电 | 红色慢闪 |
| 眼镜充电中 | 白色呼吸 |
| 眼镜充满 | 绿色闪 3 次 |
| 触点异常 | 红色快闪 3 次 |
| OTA 中 | 蓝色快闪 |
| OTA 成功 | 绿色快闪 3 次 |
| OTA 失败 | 红色快闪 5 次 |

演讲要点：

- LED 优先级建议：故障 > OTA > 盒子充电 > 眼镜充电 > 电量显示。

---

## Slide 17 - 保护阈值建议

| 项目 | 建议 |
| --- | --- |
| OTA 最低电量 | >=30% |
| 低电提示 | <20% |
| 保护关机 | <3.2V |
| 充电过温暂停 | >45C |
| 放电过温暂停 | >60C |
| 低温充电禁止 | <0C |
| 正在充电阈值 | >30mA 持续 3s |
| 通信重试 | 3 次 |

演讲要点：

- 阈值需要结合电芯、结构散热和眼镜充电电流实测后校准。

---

## Slide 18 - 测试验证计划

**硬件测试**

- 输入充电、无线充电、温升。
- 电池容量与保护。
- 左右输出限流、短路、接触阻抗。

**固件测试**

- 入仓/出仓检测。
- 充电中/满电/触点异常识别。
- LED 状态优先级。
- 通信 CRC、超时、重试。

**OTA 测试**

- 正常升级。
- 断电、拔出、丢包。
- 错误固件、低电拒绝。
- 启动失败回滚。

演讲要点：

- 充电状态判断和 OTA 回滚是验证重点。

---

## Slide 19 - 推荐量产方案

```text
MCU: 低功耗 256KB Flash
Battery: 2000mAh Li-ion + NTC
Input: USB-C + optional Qi 5W
Gauge: I2C Fuel Gauge
Output: 左右独立 Load Switch + Current Sense
Comm: Pogo Pin UART
Protocol: Box Master / Glasses Slave
OTA: Master Pull + Bootloader + Dual App
LED: RGB LED or 4 white LEDs
```

演讲要点：

- 这是可靠性优先的推荐配置。

---

## Slide 20 - 总结

**方案结论**

- 盒子作为 Master 是合理的系统控制中心。
- 眼镜作为 Slave，响应状态查询并提供 OTA 固件数据。
- “电流检测 + 眼镜上报”是判断正在充电的核心。
- OTA 使用 Master Pull，协议清晰且便于异常恢复。
- 建议量产采用 Fuel Gauge、独立输出检测和 Bootloader 回滚。

演讲要点：

- 该方案可从最小可行版本扩展到高可靠量产版本。
