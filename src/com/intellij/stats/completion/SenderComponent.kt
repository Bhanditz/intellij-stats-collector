package com.intellij.stats.completion

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.stats.completion.experiment.StatusInfoProvider
import com.intellij.util.Alarm
import com.intellij.util.Time
import org.apache.http.client.fluent.Form
import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType
import org.apache.http.util.EntityUtils
import java.io.File
import java.io.IOException
import javax.swing.SwingUtilities

fun assertNotEDT() {
    val isInTestMode = ApplicationManager.getApplication().isUnitTestMode
    assert(!SwingUtilities.isEventDispatchThread() || isInTestMode)
}

class SenderComponent(val sender: StatisticSender, val statusHelper: StatusInfoProvider) : ApplicationComponent.Adapter() {
    private val LOG = Logger.getInstance(SenderComponent::class.java)
    private val disposable = Disposer.newDisposable()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    private val sendInterval = 5 * Time.MINUTE

    private fun send() {
        if (ApplicationManager.getApplication().isUnitTestMode) return

        try {
            ApplicationManager.getApplication().executeOnPooledThread {
                statusHelper.updateStatus()
                if (statusHelper.isServerOk()) {
                    val dataServerUrl = statusHelper.getDataServerUrl()
                    sender.sendStatsData(dataServerUrl)
                }
            }
        } catch (e: Exception) {
            LOG.error(e.message)
        } finally {
            alarm.addRequest({ send() }, sendInterval)
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

class StatisticSender(val requestService: RequestService, val filePathProvider: FilePathProvider) {

    fun sendStatsData(url: String) {
        assertNotEDT()
        filePathProvider.cleanupOldFiles()
        val filesToSend = filePathProvider.getDataFiles()
        filesToSend.forEach {
            if (it.length() > 0) {
                val isSentSuccessfully = sendContent(url, it)
                if (isSentSuccessfully) {
                    it.delete()
                }
                else {
                    return
                }
            }
        }
    }

    private fun sendContent(url: String, file: File): Boolean {
        val data = requestService.post(url, file)
        if (data != null && data.code >= 200 && data.code < 300) {
            return true
        }
        return false
    }
    
}


abstract class RequestService {
    abstract fun post(url: String, params: Map<String, String>): ResponseData?
    abstract fun post(url: String, file: File): ResponseData?
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
            LOG.debug(e)
            return null
        }
    }

    override fun post(url: String, file: File): ResponseData? {
        try {
            val response = Request.Post(url).bodyFile(file, ContentType.TEXT_HTML).execute()
            val httpResponse = response.returnResponse()
            val text = EntityUtils.toString(httpResponse.entity)
            return ResponseData(httpResponse.statusLine.statusCode, text)
        }
        catch (e: IOException) {
            LOG.debug(e)
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
            LOG.debug(e)
            return null
        }
    }
}


data class ResponseData(val code: Int, val text: String = "") {
    
    fun isOK() = code >= 200 && code < 300
    
}