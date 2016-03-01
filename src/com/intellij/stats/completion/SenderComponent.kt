package com.intellij.stats.completion

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.Time
import org.apache.http.client.fluent.Form
import org.apache.http.client.fluent.Request
import org.apache.http.util.EntityUtils
import java.io.File
import java.io.IOException
import javax.swing.SwingUtilities

class SenderComponent(val sender: StatisticSender) : ApplicationComponent.Adapter() {
    private val LOG = Logger.getInstance(SenderComponent::class.java)
    private val disposable = Disposer.newDisposable()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    private val sendInterval = 1 * Time.MINUTE

    private fun send() {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            val uid = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())
            try {
                sender.sendStatsData(uid)
            }
            catch (e: Exception) {
                LOG.error(e.message)
            }
            finally {
                alarm.addRequest({ send() }, sendInterval)
            }
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
    
    private fun prepareTextToSend(fileToSend: File): String {
        if (fileToSend.exists()) {
            val text = fileToSend.readText()
            if (text.isNotEmpty()) {
                return text
            }
            fileToSend.delete()
        }
        val renamed = logFileManager.renameLogFile(fileToSend)
        return if (renamed) fileToSend.readText() else ""
    }

    fun sendStatsData(uid: String) {
        assertNotEDT()
        val fileToSend = File(FilePathProvider.getInstance().swapFile)
        var textToSend = prepareTextToSend(fileToSend)
        if (textToSend.isNotEmpty()) {
            val url = urlProvider.statsServerPostUrl
            sendContent(url, uid, textToSend, onSendAction = Runnable {
                fileToSend.delete()
            })
        }
    }

    private fun assertNotEDT() {
        val isInTestMode = ApplicationManager.getApplication().isUnitTestMode
        assert(!SwingUtilities.isEventDispatchThread() || isInTestMode)
    }

    private fun sendContent(url: String, uid: String, content: String, onSendAction: Runnable) {
        val map = mapOf(Pair("uid", uid), Pair("content", content))
        val data = requestService.post(url, map)
        if (data != null && data.code >= 200 && data.code < 300) {
            onSendAction.run()
        }
    }
    
}


abstract class RequestService {
    abstract fun post(url: String, params: Map<String, String>): ResponseData?
    abstract fun get(url: String): ResponseData?
    
    companion object {
        fun getInstance() = ServiceManager.getService(RequestService::class.java)
    }
}

class SimpleRequestService: RequestService() {
    private val LOG = Logger.getInstance(SimpleRequestService::class.java)

    override fun post(url: String, params: Map<String, String>): ResponseData? {
        val form = Form.form()
        params.forEach { form.add(it.key, it.value) }
        try {
            val response = Request.Post(url).bodyForm(form.build()).execute()
            val httpResponse = response.returnResponse()
            return ResponseData(httpResponse.statusLine.statusCode)
        } catch (e: IOException) {
            LOG.warn(e)
            return null
        }
    }

    override fun get(url: String): ResponseData? {
        try {
            var data: ResponseData? = null
            Request.Get(url).execute().handleResponse { 
                val text = EntityUtils.toString(it.entity)
                data = ResponseData(it.statusLine.statusCode, text)   
            }
            return data
        } catch (e: IOException) {
            LOG.warn(e)
            return null
        }
    }
}


data class ResponseData(val code: Int, val text: String = "") {
    
    fun isOK() = code >= 200 && code < 300
    
}