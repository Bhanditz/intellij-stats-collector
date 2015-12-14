package com.intellij.stats.completion

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.Time
import org.apache.http.client.fluent.Form
import org.apache.http.client.fluent.Request
import java.io.IOException
import javax.swing.SwingUtilities

class SenderComponent(val sender: StatisticSender) : ApplicationComponent.Adapter() {
    private val disposable = Disposer.newDisposable()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    
    private fun send() {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            val uid = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())
            sender.sendStatsData(uid)
            alarm.addRequest({ send() }, Time.MINUTE)
        }
    }
    
    override fun disposeComponent() {
        Disposer.dispose(disposable)
    }

    override fun initComponent() {
        ApplicationManager.getApplication().executeOnPooledThread {
            send()
        }
    }
}

class StatisticSender(val urlProvider: UrlProvider, val logFileManager: LogFileManager, val requestService: RequestService) {
    private val LOG = Logger.getInstance(StatisticSender::class.java)

    fun sendStatsData(uid: String) {
        assert(!SwingUtilities.isEventDispatchThread())
        try {
            logFileManager.withFileLock {
                val text = logFileManager.read()
                if (text.isNotEmpty()) {
                    val url = urlProvider.statsServerPostUrl
                    sendContent(url, uid, text, okAction = Runnable {
                        logFileManager.deleteLogFile()
                    })
                }
            }
        } catch (e: IOException) {
            LOG.error(e)
        }
    }
    
    private fun sendContent(url: String, uid: String, content: String, okAction: Runnable) {
        val map = mapOf(Pair("uid", uid), Pair("content", content))
        val data = requestService.post(url, map)
        if (data.code >= 200 && data.code < 300) {
            okAction.run()
        }
    }
    
}


abstract class RequestService {
    abstract fun post(url: String, params: Map<String, String>): ResponseData
}

class SimpleRequestService: RequestService() {

    override fun post(url: String, params: Map<String, String>): ResponseData {
        val form = Form.form()
        params.forEach {
            form.add(it.key, it.value)
        }
        val response = Request.Post(url).bodyForm(form.build()).execute()
        val httpResponse = response.returnResponse()
        return ResponseData(httpResponse.statusLine.statusCode)
    }

}


data class ResponseData(val code: Int)