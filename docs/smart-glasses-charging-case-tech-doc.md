# 智能眼镜无线充电盒仓 MCU 技术方案

## 1. 文档目的

本文档用于定义一款 2000mAh 智能眼镜充电盒仓 MCU 系统方案，覆盖硬件架构、固件功能、充电状态检测、盒子与智能眼镜通信协议、盒子 MCU OTA 升级流程以及量产测试建议。

本方案采用：

- 充电盒作为 Master。
- 智能眼镜作为 Slave。
- 充电盒主动管理电源、充电、状态轮询、LED 指示和 OTA 节奏。
- 智能眼镜被动响应盒子命令，并在盒子需要时提供盒子 MCU OTA 固件数据。

## 2. 产品目标

### 2.1 核心功能

1. 充电盒内置 2000mAh 单节锂电池。
2. 支持 USB-C 充电输入，可选 Qi 无线充电输入。
3. 支持给智能眼镜充电。
4. 支持检测智能眼镜是否入仓。
5. 支持检测智能眼镜是否已经正在充电。
6. 支持盒子自身充电状态和电量指示。
7. 支持充电盒 Master 与眼镜 Slave 通信。
8. 支持智能眼镜为盒子 MCU 提供 OTA 固件，盒子主动拉取并完成升级。

### 2.2 设计原则

- 盒子掌控电源与通信节奏。
- 充电状态采用“盒子侧电流检测 + 眼镜侧状态上报”双判断。
- OTA 采用 Master Pull 模式，避免眼镜主动推送造成总线竞争。
- 所有关键通信包包含 CRC 校验和 ACK/NACK 机制。
- OTA 支持整包校验、版本检查、失败回滚和低电保护。

## 3. 系统总体架构

```text
---------------------------------------------------------------+
|                      Smart Glasses Case                      |
|                                                               |
|  USB-C / Qi Input                                             |
|        |                                                      |
|        v                                                      |
|  +----------------+      +----------------+                  |
|  | Charger / PMIC |----->| 2000mAh Cell   |                  |
|  +----------------+      +----------------+                  |
|        |                                                      |
|        v                                                      |
|  +----------------+      +----------------+                  |
|  | Power Path     |----->| 3.3V / 1.8V    |---- MCU          |
|  +----------------+      +----------------+                  |
|        |                                                      |
|        v                                                      |
|  +----------------+      +----------------+                  |
|  | Load Switch L  |----->| Current Sense L|---- Pogo VCHG_L  |
|  +----------------+      +----------------+                  |
|                                                               |
|  +----------------+      +----------------+                  |
|  | Load Switch R  |----->| Current Sense R|---- Pogo VCHG_R  |
|  +----------------+      +----------------+                  |
|                                                               |
|  MCU Interfaces:                                             |
|  - UART to glasses                                           |
|  - I2C fuel gauge                                            |
|  - ADC battery/current/NTC                                   |
|  - GPIO hall/present/load switch/LED                         |
|  - Bootloader OTA                                            |
+---------------------------------------------------------------+
```

## 4. 硬件方案

### 4.1 MCU 建议

| 项目 | 建议规格 |
| --- | --- |
| 内核 | Cortex-M0+/M3/M4 或同级低功耗 MCU |
| Flash | >=128KB，推荐 256KB 或更高 |
| RAM | >=16KB |
| 外设 | UART、I2C、ADC、GPIO、PWM、Watchdog |
| 低功耗 | Stop/Standby 模式电流越低越好 |
| OTA | 支持 Bootloader + App 分区或双 App 分区 |

可选方向：

- STM32G0 / STM32L0 / STM32L4。
- GD32 / MM32 / HC32 / CH32 等国产 MCU。
- 如盒子需要 BLE，可考虑 nRF52 系列，但本方案优先推荐触点 UART。

### 4.2 电池与充电

| 模块 | 建议 |
| --- | --- |
| 电池 | 2000mAh 单节锂电池，3.7V 标称，4.2V/4.35V 满电 |
| 保护 | 过充、过放、过流、短路、NTC 温度保护 |
| USB-C 输入 | 5V 输入，500mA~1000mA 充电电流可配置 |
| 无线输入 | 可选 Qi 2.5W/5W 接收，输出到充电管理 IC |
| 电量计 | 推荐 Fuel Gauge，如 MAX17048、CW2015、BQ274xx |

### 4.3 眼镜充电输出

推荐左右眼镜独立充电输出：

```text
Battery / Boost / Power Path
        |
        +--> Load Switch L --> Current Sense L --> VCHG_L
        |
        +--> Load Switch R --> Current Sense R --> VCHG_R
```

每一路建议具备：

- EN 控制。
- 输出限流。
- 短路保护。
- 故障标志 FLT。
- 输出电流检测，供 MCU 判断眼镜是否正在充电。

### 4.4 触点定义

#### 左右独立充电，共用通信

```text
Pin1: VCHG_L
Pin2: GND_L
Pin3: VCHG_R
Pin4: GND_R
Pin5: BOX_TX -> GLASSES_RX
Pin6: BOX_RX <- GLASSES_TX
```

#### 单眼镜或一体式眼镜

```text
Pin1: VCHG
Pin2: GND
Pin3: BOX_TX -> GLASSES_RX
Pin4: BOX_RX <- GLASSES_TX
```

## 5. 盒子 MCU 固件架构

### 5.1 软件模块

```text
Application
  |
  +-- Power Manager
  +-- Charge Manager
  +-- Glasses Detect Manager
  +-- Communication Manager
  +-- OTA Manager
  +-- LED Manager
  +-- Fault Manager
  +-- Storage / Config Manager
  +-- Low Power Manager
  |
Bootloader
  |
  +-- Image Verify
  +-- Image Swap / Apply
  +-- Rollback
```

### 5.2 主状态机

```text
POWER_ON
  |
  v
INIT
  |
  v
IDLE
  |
  +--> GLASSES_PRESENT
  |       |
  |       +--> GLASSES_CHARGING
  |       +--> GLASSES_FULL
  |       +--> GLASSES_FAULT
  |
  +--> BOX_CHARGING
  |       |
  |       +--> BOX_FULL
  |
  +--> OTA_MODE
  |
  +--> FAULT
  |
  +--> LOW_POWER_SLEEP
```

## 6. 检测眼镜是否正在充电

### 6.1 判断原则

不建议只依赖盒子侧电流，也不建议只依赖眼镜上报。最终状态由两类信息融合：

1. 盒子侧检测：
   - 眼镜是否入仓。
   - 充电输出是否已开启。
   - 输出电压是否正常。
   - 输出电流是否超过充电阈值。
   - Load Switch / 电源芯片是否报故障。

2. 眼镜侧上报：
   - 是否检测到 VCHG。
   - 眼镜充电 IC 当前状态。
   - 眼镜电池电量、电压、温度。
   - 眼镜自身故障状态。

### 6.2 盒子侧电流阈值建议

| 状态 | 电流范围建议 |
| --- | --- |
| 未接入 | <2mA |
| 待机/通信 | 2mA~20mA |
| 正在充电 | >30mA 且持续 3s |
| 满电维持 | 2mA~30mA 且眼镜上报 full |
| 短路/异常 | 超过限流阈值或 FLT 触发 |

建议防抖：

- 采样周期：200ms~500ms。
- 连续 3~5 次采样满足条件后再切换状态。
- 左右眼镜分别维护独立状态。

### 6.3 综合状态定义

```c
typedef enum {
    GLASSES_NOT_PRESENT = 0,
    GLASSES_PRESENT_NOT_CHARGING = 1,
    GLASSES_CHARGING = 2,
    GLASSES_FULL = 3,
    GLASSES_CHARGE_FAULT = 4,
    GLASSES_CONTACT_BAD = 5,
} glasses_charge_detect_state_t;
```

### 6.4 综合判断逻辑

```text
IF not present:
    state = GLASSES_NOT_PRESENT

ELSE IF output_enabled
     AND output_current > 30mA for 3s
     AND glasses charger_state in [precharge, fast_charge, taper]:
    state = GLASSES_CHARGING

ELSE IF output_current < 30mA
     AND glasses charger_state == full:
    state = GLASSES_FULL

ELSE IF output_enabled
     AND output_current < 2mA
     AND glasses no response:
    state = GLASSES_CONTACT_BAD

ELSE IF load_switch_fault OR glasses fault_flags != 0:
    state = GLASSES_CHARGE_FAULT

ELSE:
    state = GLASSES_PRESENT_NOT_CHARGING
```

## 7. 通信协议

### 7.1 物理层

| 项目 | 建议 |
| --- | --- |
| 接口 | Pogo Pin UART |
| 主从 | 充电盒 Master，眼镜 Slave |
| 波特率 | 115200bps 默认，OTA 可切 921600bps |
| 格式 | 8N1 |
| 流控 | 无硬件流控，协议层 ACK/NACK |
| 电平 | 1.8V 或 3.3V，双方一致 |

### 7.2 地址定义

| 地址 | 设备 |
| --- | --- |
| 0x00 | 充电盒 |
| 0x01 | 左眼镜 |
| 0x02 | 右眼镜 |
| 0x03 | 整副眼镜 |
| 0xFF | 广播 |

### 7.3 数据帧格式

```text
+--------+-----+-----+-----+-----+-----+------+---------+--------+
| SOF    | Ver | Seq | Src | Dst | Cmd | Len  | Payload | CRC16  |
+--------+-----+-----+-----+-----+-----+------+---------+--------+
| 2B     | 1B  | 1B  | 1B  | 1B  | 1B  | 2B   | N bytes | 2B     |
+--------+-----+-----+-----+-----+-----+------+---------+--------+
```

字段说明：

- SOF：固定 `0xA5 0x5A`。
- Ver：协议版本，初始为 `0x01`。
- Seq：包序号，0~255 循环。
- Src：源地址。
- Dst：目标地址。
- Cmd：命令字。
- Len：Payload 长度，小端。
- Payload：业务数据。
- CRC16：对 `Ver` 到 `Payload` 做 CRC16-CCITT。

### 7.4 命令表

#### 基础命令

| Cmd | 名称 | 方向 | 说明 |
| --- | --- | --- | --- |
| 0x01 | PING | Box -> Glasses | 连通性检测 |
| 0x02 | GET_INFO | Box -> Glasses | 查询眼镜信息 |
| 0x03 | GET_STATUS | Box -> Glasses | 查询眼镜状态 |
| 0x04 | SET_MODE | Box -> Glasses | 设置眼镜模式 |
| 0x05 | SLEEP | Box -> Glasses | 进入低功耗 |
| 0x06 | ACK | 双向 | 确认 |
| 0x07 | NACK | 双向 | 错误响应 |

#### 充电命令

| Cmd | 名称 | 方向 | 说明 |
| --- | --- | --- | --- |
| 0x10 | CHARGE_ENABLE | Box -> Glasses | 通知眼镜允许充电 |
| 0x11 | CHARGE_DISABLE | Box -> Glasses | 通知眼镜停止充电 |
| 0x12 | SET_CHARGE_CURRENT | Box -> Glasses | 设置建议充电电流 |
| 0x13 | GET_BATTERY | Box -> Glasses | 获取眼镜电池信息 |
| 0x14 | GET_CHARGE_STATE | Box -> Glasses | 查询眼镜充电状态 |
| 0x15 | CHARGE_STATE_RESP | Glasses -> Box | 眼镜返回充电状态 |
| 0x16 | CHARGE_OUTPUT_STATUS | Box -> Glasses | 可选，通知盒子输出状态 |

#### 盒子状态通知

| Cmd | 名称 | 方向 | 说明 |
| --- | --- | --- | --- |
| 0x20 | BOX_STATUS_NOTIFY | Box -> Glasses | 通知盒子电量、盖子状态 |
| 0x21 | BOX_CHARGE_NOTIFY | Box -> Glasses | 通知盒子自身充电状态 |
| 0x22 | LED_STATE_NOTIFY | Box -> Glasses | 可选，通知 LED 状态 |

#### OTA 命令

| Cmd | 名称 | 方向 | 说明 |
| --- | --- | --- | --- |
| 0x30 | OTA_CHECK | Box -> Glasses | 查询眼镜是否有盒子固件 |
| 0x31 | OTA_ENTER | Box -> Glasses | 进入 OTA 会话 |
| 0x32 | OTA_GET_BLOCK | Box -> Glasses | 请求指定偏移固件数据 |
| 0x33 | OTA_BLOCK | Glasses -> Box | 返回固件数据 |
| 0x34 | OTA_VERIFY | Box -> Glasses | 通知校验结果 |
| 0x35 | OTA_APPLY | Box -> Glasses | 通知即将应用新固件 |
| 0x36 | OTA_ABORT | Box -> Glasses | 中止 OTA |
| 0x37 | OTA_PROGRESS | Box -> Glasses | 可选，通知 OTA 进度 |

## 8. 主要 Payload 定义

### 8.1 眼镜信息响应

```c
typedef struct {
    uint8_t side;                  // 1 left, 2 right
    uint8_t hw_version;
    uint8_t fw_major;
    uint8_t fw_minor;
    uint16_t fw_patch;
    uint32_t device_id;
    uint16_t protocol_version;
    uint16_t capability_flags;
} glasses_info_t;
```

### 8.2 眼镜状态响应

```c
typedef struct {
    uint8_t side;                  // 1 left, 2 right
    uint8_t present;               // 0 no, 1 yes
    uint8_t battery_soc;           // 0~100
    uint16_t battery_mv;           // mV
    uint8_t charge_state;          // 0 idle, 1 charging, 2 full, 3 fault
    int16_t temperature;           // 0.1 degC
    uint8_t mode;                  // 0 active, 1 charge, 2 sleep, 3 ota
    uint16_t fault_flags;
} glasses_status_t;
```

### 8.3 眼镜充电状态响应

```c
typedef struct {
    uint8_t side;                  // 1 left, 2 right
    uint8_t vchg_present;          // 0 no, 1 yes
    uint8_t charger_state;         // 0 idle, 1 precharge, 2 fast, 3 taper, 4 full, 5 fault
    uint8_t battery_soc;           // 0~100
    uint16_t battery_mv;           // mV
    uint16_t charge_current_ma;    // no sensor: 0xFFFF
    int16_t temperature;           // 0.1 degC
    uint16_t fault_flags;
} glasses_charge_state_t;
```

### 8.4 ACK/NACK

```c
typedef struct {
    uint8_t ack_cmd;
    uint8_t result;                // 0 success
} ack_payload_t;

typedef struct {
    uint8_t nack_cmd;
    uint8_t error_code;
} nack_payload_t;
```

错误码：

| Error | 含义 |
| --- | --- |
| 0x01 | CRC 错误 |
| 0x02 | 命令不支持 |
| 0x03 | 参数错误 |
| 0x04 | 状态不允许 |
| 0x05 | Flash 写入失败 |
| 0x06 | 电量不足 |
| 0x07 | 温度异常 |
| 0x08 | 固件校验失败 |
| 0x09 | 超时 |
| 0x0A | 版本不允许 |

## 9. 盒子 Master 轮询流程

```text
Glasses inserted
  |
  v
Enable communication / charge rail
  |
  v
Wait 50~200ms
  |
  v
PING
  |
  +-- no response after 3 retries --> contact fault
  |
  v
GET_INFO
  |
  v
GET_STATUS
  |
  v
GET_CHARGE_STATE
  |
  v
Update charging state and LED
  |
  v
Periodic polling
```

建议轮询周期：

| 状态 | 周期 |
| --- | --- |
| 刚入仓 | 500ms |
| 正在充电 | 2s~5s |
| 已充满 | 10s~30s |
| 盒盖关闭且稳定 | 30s~60s |
| OTA 中 | 由 OTA block 传输节奏决定 |

## 10. 盒子 MCU OTA 方案

### 10.1 OTA 角色

盒子作为 Master，因此 OTA 采用 Master Pull：

- 盒子主动询问眼镜是否保存了盒子 MCU 新固件。
- 眼镜作为 Slave，只响应盒子的 OTA 请求。
- 盒子按 offset 主动拉取固件 block。
- 盒子本地完成写入、校验、切换和重启。

### 10.2 Flash 分区建议

#### 推荐方案：Bootloader + 双 App

```text
Bootloader
App A
App B
OTA Metadata
Config
```

优点：

- OTA 失败可回滚。
- 新旧固件切换安全。
- 适合量产。

#### 备选方案：Bootloader + App + Download Area

```text
Bootloader
App
OTA Download Area
Metadata
Config
```

适合外置 SPI Flash 或 MCU Flash 空间较大的设计。

### 10.3 OTA 流程

```text
Box Master                          Glasses Slave
    |                                      |
    |---- PING -------------------------->|
    |<--- ACK ----------------------------|
    |                                      |
    |---- OTA_CHECK --------------------->|
    |<--- has_update/version/size/crc ----|
    |                                      |
    |---- OTA_ENTER --------------------->|
    |<--- ACK ----------------------------|
    |                                      |
    |---- OTA_GET_BLOCK offset=0 -------->|
    |<--- OTA_BLOCK data -----------------|
    |                                      |
    |---- OTA_GET_BLOCK offset=512 ------>|
    |<--- OTA_BLOCK data -----------------|
    |                                      |
    |---- OTA_GET_BLOCK offset=N -------->|
    |<--- OTA_BLOCK data -----------------|
    |                                      |
    |---- OTA_VERIFY -------------------->|
    |<--- ACK ----------------------------|
    |                                      |
    |---- OTA_APPLY --------------------->|
    |<--- ACK ----------------------------|
    |                                      |
    |        Box reboot into new firmware  |
```

### 10.4 OTA_CHECK 响应

```c
typedef struct {
    uint8_t has_update;            // 0 no, 1 yes
    uint8_t target_device;         // 0 box, 1 left, 2 right
    uint8_t version_major;
    uint8_t version_minor;
    uint16_t version_patch;
    uint32_t image_size;
    uint32_t image_crc32;
    uint8_t image_sha256[32];
    uint8_t mandatory;
} ota_check_resp_t;
```

### 10.5 OTA_GET_BLOCK / OTA_BLOCK

```c
typedef struct {
    uint32_t offset;
    uint16_t length;               // 128/256/512 bytes
} ota_get_block_t;

typedef struct {
    uint32_t offset;
    uint16_t length;
    uint8_t data[];
} ota_block_t;
```

### 10.6 OTA 保护策略

| 项目 | 策略 |
| --- | --- |
| 盒子电量 | SOC <30% 拒绝 OTA |
| 温度 | 低温/高温异常拒绝 OTA |
| 数据校验 | 帧 CRC16 + 整包 CRC32/SHA256 |
| 固件安全 | 量产建议增加 ECDSA/RSA 签名 |
| 超时 | 单包 100ms~500ms 超时，重试 3 次 |
| block 错误 | 重新请求当前 offset |
| 连续失败 | 失败 5 次后 OTA_ABORT |
| 回滚 | 新固件启动失败则回滚旧固件 |

## 11. LED 指示策略

### 11.1 RGB LED 方案

| 状态 | 指示 |
| --- | --- |
| 盒子充电中 | 橙色慢闪 |
| 盒子满电 | 绿色常亮 10s 后熄灭 |
| 盒子低电 | 红色慢闪 |
| 眼镜正在充电 | 白色呼吸 |
| 眼镜充满 | 绿色闪 3 次 |
| 触点异常 | 红色快闪 3 次 |
| OTA 中 | 蓝色快闪 |
| OTA 成功 | 绿色快闪 3 次 |
| OTA 失败 | 红色快闪 5 次 |
| 过温/短路故障 | 红色常亮或快闪 |

### 11.2 四颗白灯方案

| 电量 | LED |
| --- | --- |
| 0~25% | 1 颗 |
| 26~50% | 2 颗 |
| 51~75% | 3 颗 |
| 76~100% | 4 颗 |

## 12. 故障定义

### 12.1 盒子故障位

```text
bit0: 盒子电池低电
bit1: 盒子电池过温
bit2: 盒子电池低温
bit3: 输入过压
bit4: 眼镜输出短路
bit5: 触点异常
bit6: OTA 校验失败
bit7: Flash 写入失败
bit8: Fuel Gauge 异常
```

### 12.2 眼镜故障位

```text
bit0: 眼镜低电
bit1: 眼镜过温
bit2: 眼镜低温
bit3: 充电异常
bit4: 触点异常
bit5: 电池异常
bit6: OTA 忙
bit7: 固件异常
```

## 13. 低功耗策略

| 场景 | 策略 |
| --- | --- |
| 盒盖关闭且无通信 | MCU 进入 Stop/Standby |
| 无眼镜入仓 | 关闭眼镜充电输出 |
| 眼镜满电 | 降低轮询频率，必要时关闭输出 |
| OTA 中 | 禁止深睡 |
| 过温/短路 | 关闭对应输出并进入故障状态 |

## 14. 关键阈值建议

| 项目 | 建议阈值 |
| --- | --- |
| OTA 最低盒子电量 | >=30% |
| 低电提示 | <20% |
| 保护关机 | <3.2V |
| 充电过温暂停 | >45C |
| 放电过温暂停 | >60C |
| 低温充电禁止 | <0C |
| 正在充电电流阈值 | >30mA 持续 3s |
| 通信重试 | 3 次 |
| OTA 连续失败 | 5 次后中止 |

## 15. 测试与验证建议

### 15.1 硬件验证

- USB-C 输入充电电流和充满截止。
- Qi 无线输入功率、温升和异物发热风险。
- 2000mAh 电池容量、保护板动作点。
- 左右眼镜输出限流和短路保护。
- 触点 ESD、接触电阻、插拔寿命。
- NTC 温度采样准确性。
- Fuel Gauge SOC 精度。

### 15.2 固件验证

- 入仓检测和出仓检测。
- 左右眼镜独立充电状态判断。
- 眼镜正在充电/满电/异常状态切换。
- LED 显示优先级。
- 通信 CRC 错误、超时、重试。
- 低功耗唤醒。
- 看门狗恢复。

### 15.3 OTA 验证

- 正常升级。
- OTA 过程中断电。
- OTA 过程中拔出眼镜。
- OTA 过程中通信丢包。
- 错误固件版本。
- 错误 CRC/SHA256。
- 低电量拒绝 OTA。
- 新固件启动失败回滚。

## 16. 推荐落地配置

### 16.1 标准量产方案

```text
MCU: Low-power Cortex-M0+ / M3, 256KB Flash
Battery: 2000mAh Li-ion + NTC + protection
Input: USB-C 5V, optional Qi 5W receiver
Fuel Gauge: I2C gauge
Charge output: independent left/right load switches with current sensing
Communication: UART over pogo pin
Protocol: Box Master, Glasses Slave, CRC16, ACK/NACK
OTA: Master Pull, Bootloader + dual App, CRC32/SHA256/signature
LED: RGB LED or 4 white LEDs
```

### 16.2 最小可行方案

```text
MCU: 128KB Flash low-power MCU
Battery: 2000mAh Li-ion
Input: USB-C only
Battery SOC: ADC voltage estimation
Charge output: load switch with FLT pin
Communication: UART over pogo pin
OTA: Bootloader + download area
LED: single RGB LED
```

## 17. 结论

本方案建议使用“充电盒 Master + 眼镜 Slave”的系统架构。盒子负责电源控制、充电管理、状态轮询、LED 指示和 OTA 主流程；眼镜负责响应状态查询并按需提供盒子 MCU 固件数据。眼镜是否正在充电应采用盒子侧电流检测与眼镜侧充电状态上报的双重判断，以提升可靠性和量产可测性。
