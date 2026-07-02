# Luna StateMachine

提供两套状态机实现，适用于不同的使用场景：

| 实现 | 包路径 | 特点 |
|------|--------|------|
| **`SampleStateMachine`**（推荐） | `com.lunacattus.statemachine.sample` | Kotlin Coroutines 协程驱动，平级状态，类型安全，轻量 |
| **`StateMachine`**（经典） | `com.lunacattus.statemachine` | 消息驱动，层级状态，Handler/Looper 机制，Android 平台移植 |

---

## 一、SampleStateMachine（协程版，推荐）

基于 Kotlin Coroutines `Channel` 的轻量级平级状态机，适合现代 Android/Kotlin 项目。

### 添加依赖

```kotlin
dependencies {
    implementation("com.lunacattus.android:statemachine:1.0.0")
}
```

### 基本用法

#### 1. 定义事件类型（推荐 sealed interface）

```kotlin
sealed interface Event {
    data object Start : Event
    data object Stop : Event
    data class Update(val value: Int) : Event
}
```

#### 2. 定义状态

```kotlin
class IdleState(private val machine: SampleStateMachine<Event>) : ISampleState<Event> {
    override val name = "Idle"

    override suspend fun enter() {
        println("进入 Idle 状态")
    }

    override suspend fun exit() {
        println("离开 Idle 状态")
    }

    override suspend fun processEvent(event: Event): Boolean = when (event) {
        is Event.Start -> {
            machine.transitionTo(RunningState(machine))
            true // 已处理
        }
        else -> false // 不处理，触发 onUnhandledEvent
    }
}

class RunningState(private val machine: SampleStateMachine<Event>) : ISampleState<Event> {
    override val name = "Running"

    override suspend fun enter() {
        println("进入 Running 状态")
    }

    override suspend fun exit() {
        println("离开 Running 状态")
    }

    override suspend fun processEvent(event: Event): Boolean = when (event) {
        is Event.Stop -> {
            machine.transitionTo(IdleState(machine))
            true
        }
        else -> false
    }
}
```

#### 3. 构建与使用

```kotlin
val scope = CoroutineScope(Dispatchers.Default)
val machine = SampleStateMachine<Event>(scope, "MyMachine")

machine.addState(IdleState(machine))
machine.addState(RunningState(machine))
machine.setInitialState(IdleState(machine))
machine.start()

machine.sendEvent(Event.Start)  // Idle → Running
machine.sendEvent(Event.Stop)   // Running → Idle
machine.quit()
```

### API 总览

#### 公共方法

| 方法 | 说明 |
|------|------|
| `addState(state)` | 注册一个状态到状态机 |
| `setInitialState(state)` | 设置初始状态（须在 `start()` 前调用） |
| `start()` | 启动状态机，自动进入初始状态 |
| `sendEvent(event)` | 发送事件到消息队列中异步处理 |
| `transitionTo(state)` | 请求切换到目标状态（在 `processEvent` 内调用） |
| `deferEvent(event)` | 将事件放入延迟队列，下次状态切换后重新处理 |
| `quit()` | 停止状态机 |
| `getCurrentState()` | 获取当前状态引用 |
| `isStarted()` / `isQuit()` | 查询状态机运行状态 |

#### 可观察属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `currentState` | `StateFlow<ISampleState<E>?>` | 当前状态，可安全收集（collect） |

#### 可重写的错误处理钩子（protected open）

| 方法 | 触发时机 |
|------|---------|
| `onUnhandledEvent(event, state)` | 事件未被任何状态处理 |
| `onEventError(event, state, e)` | `processEvent` 中抛出异常 |
| `onStateEnterError(state, e)` | 状态的 `enter()` 中抛出异常 |
| `onStateExitError(state, e)` | 状态的 `exit()` 中抛出异常 |
| `onQuitting()` | 状态机即将退出时回调 |

#### 辅助接口和基类

| 类型 | 说明 |
|------|------|
| `ISampleState<E>` | 状态接口，定义 `enter()` / `exit()` / `processEvent()` / `name` |
| `SimpleState<E>` | `ISampleState` 的空实现基类，仅继承最简实现 |

### 延迟事件示例

在某个状态中通过 `deferEvent` 将事件延迟到状态切换后处理：

```kotlin
class LoadingState(private val machine: SampleStateMachine<Event>) : ISampleState<Event> {
    override val name = "Loading"

    override suspend fun processEvent(event: Event): Boolean = when (event) {
        is Event.DataLoaded -> {
            machine.deferEvent(Event.Initialize) // 切换到 Ready 后再处理
            machine.transitionTo(ReadyState(machine))
            true
        }
        else -> false
    }
}
```

---

## 二、StateMachine（Handler 版，经典）

移植自 Android 平台 `android.os.StateMachine` 的层级状态机。
- 基于 `Handler`/`Message`/`Looper`，自有独立线程或绑定外部 Looper
- 状态可形成父子层级，子状态未处理的消息自动向父状态冒泡
- 内置环形缓冲区日志记录系统

### 基本用法

```java
class MyStateMachine extends StateMachine {

    // 消息定义
    private static final int CMD_START = 1;
    private static final int CMD_STOP = 2;

    // 状态实例
    private State mIdle = new IdleState();
    private State mRunning = new RunningState();

    MyStateMachine(String name) {
        super(name);
        addState(mIdle);
        addState(mRunning);
        setInitialState(mIdle);
    }

    public static MyStateMachine create() {
        MyStateMachine sm = new MyStateMachine("MyMachine");
        sm.start();
        return sm;
    }

    class IdleState extends State {
        @Override
        public void enter() {
            log("进入 Idle");
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case CMD_START:
                    transitionTo(mRunning);
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class RunningState extends State {
        @Override
        public void enter() {
            log("进入 Running");
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case CMD_STOP:
                    transitionTo(mIdle);
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }
}

// 使用
MyStateMachine sm = MyStateMachine.create();
sm.sendMessage(sm.obtainMessage(CMD_START));
sm.sendMessage(sm.obtainMessage(CMD_STOP));
```

### API 总览

#### 构造方式

| 构造方法 | 说明 |
|---------|------|
| `StateMachine(name)` | 创建自带独立 HandlerThread 的状态机 |
| `StateMachine(name, Looper)` | 绑定到指定的 Looper |
| `StateMachine(name, Handler)` | 绑定到指定的 Handler |

#### 状态管理

| 方法 | 说明 |
|------|------|
| `addState(state, parent?)` | 注册状态，可指定父状态实现层级 |
| `removeState(state)` | 移除状态（不能是活跃状态或拥有子状态） |
| `setInitialState(state)` | 设置初始状态 |
| `start()` | 启动状态机 |
| `transitionTo(destState)` | 请求切换到目标状态 |
| `transitionToHaltingState()` | 切换到停止态，后续消息由 `haltedProcessMessage` 处理 |

#### 消息发送

| 方法 | 说明 |
|------|------|
| `obtainMessage(...)` | 获取 Message 对象（多种重载） |
| `sendMessage(...)` | 发送消息到队列尾部（多种重载） |
| `sendMessageDelayed(...)` | 延时发送消息（多种重载） |
| `sendMessageAtFrontOfQueue(...)` | 插队到队列头部（protected） |

#### 延迟消息

| 方法 | 说明 |
|------|------|
| `deferMessage(msg)` | 延迟消息，在下次状态切换后放到队列头部重新处理 |
| `removeDeferredMessages(what)` | 移除延迟队列中的指定消息 |
| `hasDeferredMessages(what)` | 检查延迟队列中是否有指定消息 |

#### 生命周期回调（protected，可重写）

| 方法 | 触发时机 |
|------|---------|
| `unhandledMessage(msg)` | 当前状态及所有父状态均未处理该消息 |
| `haltedProcessMessage(msg)` | 已进入停止态后收到消息 |
| `onHalting()` | 进入停止态时回调 |
| `onQuitting()` | 退出时回调 |
| `onPreHandleMessage(msg)` | 每次处理消息前回调 |
| `onPostHandleMessage(msg)` | 每次处理消息后回调 |

#### 日志记录系统

| 方法 | 说明 |
|------|------|
| `setLogRecSize(maxSize)` | 设置日志记录环形缓冲区大小 |
| `setLogOnlyTransitions(enable)` | 是否仅记录触发状态切换的消息 |
| `getLogRecSize()` / `getLogRec(index)` / `copyLogRecs()` | 读取日志记录 |
| `addLogRec(string)` | 手动添加一条日志记录 |
| `log()` / `logd()` / `logv()` / `logi()` / `logw()` / `loge()` | 输出日志（protected） |

#### 辅助类型

| 类型 | 说明 |
|------|------|
| `IState` | 状态接口：`enter()` / `exit()` / `processMessage()` / `getName()` |
| `State` | `IState` 的默认空实现基类 |
| `LogRec` | 日志记录结构体（时间戳、消息、状态转换信息） |

#### 其他

| 方法 | 说明 |
|------|------|
| `quit()` | 处理完当前队列消息后退出 |
| `quitNow()` | 立即退出，丢弃队列中剩余消息 |
| `getCurrentState()` / `getCurrentMessage()` | 获取当前状态和正在处理的消息 |
| `getHandler()` | 获取内部 Handler |
| `getName()` | 获取状态机名称 |
| `setDbg(enable)` / `isDbg()` | 调试模式开关 |

---

## 三、日志回调配置

两种状态机实现均通过 `StateMachineLog` 输出日志。使用方可通过 `setLogger` 注入自定义日志处理：

```kotlin
// Kotlin
StateMachineLog.setLogger(
    debug = { tag, msg -> MyLogger.d(tag, msg) },
    error = { tag, msg, tr -> MyLogger.e(tag, msg, tr) }
)
```

```java
// Java
StateMachineLog.setLogger(
    (tag, msg) -> MyLogger.d(tag, msg),
    (tag, msg, tr) -> MyLogger.e(tag, msg, tr)
);
```

不调用 `setLogger` 时，默认通过 `android.util.Log` 输出。
建议在 Application.onCreate 中完成配置。

---

## 四、如何选择

| 场景 | 推荐 |
|------|------|
| Kotlin 项目、协程化 | **SampleStateMachine** |
| 需要状态层级（父/子状态冒泡） | **StateMachine** |
| Java 项目或遗留代码 | **StateMachine** |
| 需要实时观察当前状态（StateFlow） | **SampleStateMachine** |
| 需要内置日志记录检索 | **StateMachine** |
| 简单状态切换、轻量化 | **SampleStateMachine** |

---

## 五、Maven 坐标

| group | artifact | version |
|-------|----------|---------|
| `com.lunacattus.android` | `statemachine` | `1.0.0` |

