package com.houseaffairs.app.core.data

/**
 * Giving a task to someone: they say yes or "can't do it", and a "can't" goes back to whoever
 * gave it, with the reason. How each person sees a task follows from these rules.
 */
enum class Handoff {
    /** Nothing to answer. */
    NONE,
    /** Someone gave it to me: Accept or Can't do it. */
    ASKED_ME,
    /** I gave it to someone who hasn't answered. */
    WAITING,
    /** I gave it and they said yes. */
    ACCEPTED,
    /** I gave it, they can't, so it's back with me: do it myself or ask someone else. */
    CAME_BACK,
}

fun handoffOf(task: TaskItem, me: String): Handoff = when {
    task.ask == "PENDING" && task.assignee == me && task.assignedBy != me -> Handoff.ASKED_ME
    task.ask == "PENDING" && task.assignedBy == me && task.assignee != me -> Handoff.WAITING
    task.ask == "ACCEPTED" && task.assignedBy == me && task.assignee != me -> Handoff.ACCEPTED
    task.ask == "DECLINED" && task.assignee == me && task.declinedBy != null && task.declinedBy != me -> Handoff.CAME_BACK
    else -> Handoff.NONE
}

/** Asking only makes sense for someone else, and a rota turn was agreed when the rota was set up. */
fun needsAnswer(assignee: String?, givenBy: String, rota: Boolean): Boolean = assignee != null && assignee != givenBy && !rota

/** Tasks asked of me, or that came back to me, first: they wait on me. */
fun waitingOnMe(tasks: List<TaskItem>, me: String): List<TaskItem> =
    tasks.filter { handoffOf(it, me) == Handoff.ASKED_ME || handoffOf(it, me) == Handoff.CAME_BACK }

/** The line under a task's title: "Asha asked you", "Waiting for Ravi", "Ravi can't: away Saturday". */
fun handoffLine(task: TaskItem, me: String, nameOf: (String) -> String): String? = when (handoffOf(task, me)) {
    Handoff.ASKED_ME -> "${task.assignedBy?.let(nameOf) ?: "Someone"} asked you"
    Handoff.WAITING -> "Waiting for ${task.assignee?.let(nameOf) ?: "them"} to say yes"
    Handoff.ACCEPTED -> "${task.assignee?.let(nameOf) ?: "They"} said yes"
    Handoff.CAME_BACK -> "${task.declinedBy?.let(nameOf) ?: "They"} can't do it" + (task.declineNote?.let { ": $it" } ?: "")
    Handoff.NONE -> null
}
