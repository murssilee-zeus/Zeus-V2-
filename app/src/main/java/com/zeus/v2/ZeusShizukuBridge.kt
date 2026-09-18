package com.zeus.v2

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

/** Shizuku bridge for the privileged Zeus audio backend. */
object ZeusShizukuBridge {
    private const val TAG = "ZeusShizuku"
    const val REQUEST_CODE = 18018
    enum class State { UNAVAILABLE, NOT_AUTHORIZED, AUTHORIZED }
    @Volatile var state: State = State.UNAVAILABLE
        private set
    @Volatile var remoteUid: Int = -1
        private set
    @Volatile var selinuxContext: String? = null
        private set

    fun refresh(): State {
        return try {
        if (!Shizuku.pingBinder()) {
            state = State.UNAVAILABLE; remoteUid = -1; selinuxContext = null; return state
        }
        remoteUid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        selinuxContext = runCatching { Shizuku.getSELinuxContext() }.getOrNull()
        state = if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) State.AUTHORIZED else State.NOT_AUTHORIZED
        state
    } catch (t: Throwable) {
        Log.w(TAG, "Shizuku refresh failed: " + t.message)
        state = State.UNAVAILABLE; remoteUid = -1; selinuxContext = null; state
        }
    }

    fun requestPermission(): Boolean = try {
        if (!Shizuku.pingBinder()) { refresh(); false }
        else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { state = State.AUTHORIZED; true }
        else { Shizuku.requestPermission(REQUEST_CODE); false }
    } catch (t: Throwable) {
        Log.w(TAG, "Shizuku permission request failed: " + t.message); refresh(); false
    }

    fun isAuthorized(): Boolean = refresh() == State.AUTHORIZED

    fun privilegeLabel(): String {
        refresh()
        return when (state) {
            State.AUTHORIZED -> when (remoteUid) {
                0 -> "Shizuku ROOT"
                2000 -> "Shizuku ADB/SHELL"
                else -> "Shizuku UID " + remoteUid
            }
            State.NOT_AUTHORIZED -> "Shizuku sin autorización"
            State.UNAVAILABLE -> "Shizuku no disponible"
        }
    }

    fun initialize() {
        try {
            Shizuku.addBinderReceivedListenerSticky { refresh() }
            Shizuku.addBinderDeadListener { refresh() }
            refresh()
            Log.i(TAG, "Backend state=" + state + " privilege=" + privilegeLabel())
        } catch (t: Throwable) {
            Log.w(TAG, "Shizuku initialization failed: " + t.message)
        }
    }
}