package com.reference.implementation.data.audit

import android.util.Log

class RemoteAudit {

    companion object Logger {
        fun createInstance(): RemoteAudit {
            return RemoteAudit()
        }
    }

    // FUTURE FEATURE - write the message to an audit logger repository or SIEM.
    fun writeLog(message: String) {
        Log.d("audit", message)
    }
}

fun auditLog(message: String) {
    RemoteAudit.createInstance().writeLog(message)
}