package fan.x0.focus.data.device

fun DeviceShellSessionInfo.describe(): String =
    "$id\tshell=$shell\troot=$root\talive=$alive\tstarted=$startedAt"

fun DeviceBgJobInfo.describe(): String {
    val state = if (running) "running" else "ended"
    val timeout = if (timedOut) " timed_out" else ""
    return "$id\t$state\texit=$exitCode$timeout\tstarted=$startedAt\tended=$endedAt\t$command"
}

fun DeviceEventInfo.describe(): String {
    val body = text.ifBlank { className }
    return "$seq\t$type\t$packageName\t$body"
}

fun DeviceTrigger.describe(): String {
    val state = if (enabled) "enabled" else "disabled"
    return "$id\t$name\t$state\taction=$action\tpackage=$packageFilter\ttext=$textFilter\ttype=$typeFilter\tcooldown=${cooldownSec}s\t$command"
}
