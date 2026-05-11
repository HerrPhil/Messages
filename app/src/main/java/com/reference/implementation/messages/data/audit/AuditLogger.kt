package com.reference.implementation.messages.data.audit

class Audit {

    companion object Logger {
        fun createInstance(): Audit {
            return Audit()
        }
    }

    fun writeLog(message: String) {
        // TODO write. the message to an audit logger repository or SIEM
    }
}