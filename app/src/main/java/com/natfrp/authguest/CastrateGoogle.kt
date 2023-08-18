@file:Suppress("unused", "UNUSED_PARAMETER")

package com.google.android.gms.common.internal

import android.app.Activity
import android.content.Context
import android.os.Parcel
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import java.util.concurrent.Executor

class TelemetryLoggingOptions {
    class Builder {
        fun setApi(api: String?): Builder = this
        fun build(): TelemetryLoggingOptions = TelemetryLoggingOptions()
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}

private object DummyLogTask : Task<Void?>() {
    override fun addOnFailureListener(p0: OnFailureListener): Task<Void?> {
        // Implemented, because failing tells MLKit to back-off for 30 minutes, which is a win for performance
        p0.onFailure(exception)
        return this
    }
    override fun addOnFailureListener(p0: Activity, p1: OnFailureListener): Task<Void?> = addOnFailureListener(p1)
    override fun addOnFailureListener(p0: Executor, p1: OnFailureListener): Task<Void?> = addOnFailureListener(p1)

    override fun addOnSuccessListener(p0: OnSuccessListener<in Void?>): Task<Void?> = this
    override fun addOnSuccessListener(p0: Activity, p1: OnSuccessListener<in Void?>): Task<Void?> = addOnSuccessListener(p1)
    override fun addOnSuccessListener(p0: Executor, p1: OnSuccessListener<in Void?>): Task<Void?> = addOnSuccessListener(p1)

    override fun getException(): Exception? = exception
    override fun getResult(): Void? = null
    override fun <X : Throwable?> getResult(p0: Class<X>): Void? = null

    override fun isCanceled(): Boolean = false
    override fun isComplete(): Boolean = true
    override fun isSuccessful(): Boolean = false

    private val exception = Exception("Success was never an option")
}

object TelemetryLogging {
    @JvmStatic
    fun getClient(context: Context): TelemetryLoggingClient {
        return object : TelemetryLoggingClient {
            override fun log(data: TelemetryData): Task<Void?> {
                return DummyLogTask
            }
        }
    }

    @JvmStatic
    fun getClient(context: Context, options: TelemetryLoggingOptions): TelemetryLoggingClient {
        return getClient(context)
    }
}

interface TelemetryLoggingClient {
    fun log(data: TelemetryData): Task<Void?>
}

class TelemetryData(var1: Int, var2:List<MethodInvocation>?) {
    fun writeToParcel(var1: Parcel, var2: Int) {}
}

class MethodInvocation {

    constructor(methodKey:Int, resultStatusCode:Int, connectionResultStatusCode:Int,
                startTimeMillis:Long, endTimeMillis:Long,
                callingModuleId: String?, callingEntryPoint: String?, serviceId:Int)

    constructor(methodKey:Int, resultStatusCode:Int, connectionResultStatusCode:Int,
                startTimeMillis:Long, endTimeMillis:Long,
                callingModuleId: String?, callingEntryPoint: String?,
                serviceId:Int, var11:Int)

    fun writeToParcel(var1: Parcel, var2: Int) {}
}