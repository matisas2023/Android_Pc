package com.example.android_project

sealed class Routes(val route: String) {
    data object Mouse : Routes("mouse")
    data object Keyboard : Routes("keyboard")
    data object System : Routes("system")
    data object Screenshot : Routes("screenshot")
    data object Session : Routes("session")
    data object Media : Routes("media")
}
