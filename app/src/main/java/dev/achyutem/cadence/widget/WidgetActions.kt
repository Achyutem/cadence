package dev.achyutem.cadence.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import dev.achyutem.cadence.core.database.entity.HabitEntryEntity
import dev.achyutem.cadence.core.database.entity.HabitType
import dev.achyutem.cadence.domain.habit.isValueComplete
import kotlinx.coroutines.flow.first

/**
 * Interactive widget actions.
 *
 * These are the point of the widget system: ticking a task or logging a glass of water from the
 * home screen must **not open the app**. Each callback writes through the same DAOs the app uses
 * and then asks every widget to refresh, so a task completed on one widget updates the others
 * immediately.
 *
 * `ActionCallback` runs on a background dispatcher, so suspending database work is safe here.
 */
object WidgetKeys {
    val taskId = ActionParameters.Key<Long>("taskId")
    val habitId = ActionParameters.Key<Long>("habitId")
    val completed = ActionParameters.Key<Boolean>("completed")
    val delta = ActionParameters.Key<Float>("delta")
}

fun taskParams(taskId: Long, completed: Boolean) =
    actionParametersOf(WidgetKeys.taskId to taskId, WidgetKeys.completed to completed)

fun habitParams(habitId: Long, delta: Float = 1f) =
    actionParametersOf(WidgetKeys.habitId to habitId, WidgetKeys.delta to delta)

/** Tick or untick a task. */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[WidgetKeys.taskId] ?: return
        val completed = parameters[WidgetKeys.completed] ?: return
        val container = context.appContainer()
        val at = container.clock.now()

        // A parent's completion is derived from its children, so completing one from a widget has
        // to complete the children too — exactly as the in-app path does.
        container.taskDao.observeSubtasks(taskId).first().forEach { child ->
            container.taskDao.setCompleted(child.id, completed, if (completed) at else null)
        }
        container.taskDao.setCompleted(taskId, completed, if (completed) at else null)

        CadenceWidgets.updateAll(context)
    }
}

/**
 * Step a habit.
 *
 * A boolean habit flips; everything else moves by its own increment. The completion flag is
 * evaluated against the habit's current target and stored, the same rule the app uses so a target
 * raised later cannot retroactively un-complete a day.
 */
class StepHabitAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val habitId = parameters[WidgetKeys.habitId] ?: return
        val direction = parameters[WidgetKeys.delta] ?: 1f
        val container = context.appContainer()
        val entity = container.habitDao.getById(habitId) ?: return

        val date = container.clock.today()
        val at = container.clock.now()
        val existing = container.habitDao.getEntry(habitId, date)
        val current = existing?.value ?: 0.0

        val step = when (entity.type) {
            HabitType.BOOLEAN -> entity.targetValue.coerceAtLeast(1.0)
            HabitType.COUNT, HabitType.QUANTITY -> 1.0
            HabitType.DURATION -> 5.0
        }
        val next = when {
            entity.type == HabitType.BOOLEAN ->
                if (current >= entity.targetValue) 0.0 else entity.targetValue.coerceAtLeast(1.0)
            direction < 0 -> (current - step).coerceAtLeast(0.0)
            else -> current + step
        }

        container.habitDao.upsertEntry(
            existing?.copy(
                value = next,
                completed = entity.isValueComplete(next),
                updatedAt = at,
            ) ?: HabitEntryEntity(
                habitId = habitId,
                date = date,
                value = next,
                completed = entity.isValueComplete(next),
                createdAt = at,
                updatedAt = at,
            )
        )

        CadenceWidgets.updateAll(context)
    }
}

/**
 * Open the app from a widget's background. Every widget's surface is tappable.
 *
 * Built from an explicit component rather than a reified activity type: the reified overload is
 * not available in this Glance version, and naming the component keeps the widget working even
 * when the launcher resolves it from a different process.
 */
@androidx.compose.runtime.Composable
fun openAppAction(): androidx.glance.action.Action {
    val context = androidx.glance.LocalContext.current
    return androidx.glance.appwidget.action.actionStartActivity(
        android.content.Intent(context, dev.achyutem.cadence.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
