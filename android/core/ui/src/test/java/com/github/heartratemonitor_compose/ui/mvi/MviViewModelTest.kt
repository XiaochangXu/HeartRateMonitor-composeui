package com.github.heartratemonitor_compose.ui.mvi

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test

/**
 * [MviViewModel] 基类单元测试：
 * - 并发 setState 不丢更新（CAS 归约）；
 * - dispatch 不阻塞调用线程（异步投递到 viewModelScope）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MviViewModelTest {

    private data class CountState(val count: Int = 0)

    private sealed interface TestIntent {
        data object Increment : TestIntent
        data class Add(val amount: Int) : TestIntent
    }

    private class TestViewModel : MviViewModel<CountState, TestIntent>(CountState()) {
        override suspend fun handleIntent(intent: TestIntent) {
            when (intent) {
                TestIntent.Increment -> setState { it.copy(count = it.count + 1) }
                is TestIntent.Add -> setState { it.copy(count = it.count + intent.amount) }
            }
        }

        /** 暴露内部快照供测试断言（生产子类不应开放）。 */
        fun peek(): CountState = currentState
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `concurrent setState keeps every update via CAS`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = TestViewModel()
        val threads = 8
        val perThread = 1000

        val jobs = (1..threads).map {
            launch(kotlinx.coroutines.Dispatchers.Default) {
                repeat(perThread) {
                    viewModel.dispatch(TestIntent.Increment)
                }
            }
        }
        jobs.forEach { it.join() }
        runCurrent()

        // CAS 归约保证无丢失更新：总数必须精确等于全部意图之和
        assertThat(viewModel.uiState.value.count).isEqualTo(threads * perThread)
    }

    @Test
    fun `dispatch returns immediately and handler runs on viewModelScope`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = TestViewModel()

        // dispatch 本身不做任何挂起工作：调度器尚未推进时状态保持初值
        viewModel.dispatch(TestIntent.Add(3))
        viewModel.dispatch(TestIntent.Add(4))
        assertThat(viewModel.uiState.value.count).isEqualTo(0)

        // 推进主调度器后意图按序归约
        runCurrent()
        assertThat(viewModel.uiState.value.count).isEqualTo(7)
        assertThat(viewModel.peek().count).isEqualTo(7)
    }
}
