package com.example.tips

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.Socket


class RemoteTunnel(username : String, password : String, devSecret : String, devId : String) {

    private var mediaType = "application/json; charset=utf-8".toMediaType()
    private var remoteServer : Socket

    init {
        val authJson = JSONObject(getToken(username, password, devSecret))
        val token = authJson.getString("token")
        val proxyJson = JSONObject(getProxy(token, devId, devSecret))
        val url = proxyJson.getJSONObject("connection").getString("proxyserver")
        val port = proxyJson.getJSONObject("connection").getString("proxyport").toInt()
        remoteServer = Socket(url, port)
    }

    /* http post request to get auth token */
    @Throws(IOException::class)
    private fun getToken(username : String, password : String, secret : String): String {

        var client = OkHttpClient()
        val json = "{ \"username\" : \"" + username + "\", \"password\" : \"" + password + "\" }"
        val requestBody = json.toRequestBody(mediaType)
        val request = Request.Builder()
                .header("developerkey", secret)
                .url("https://api.remot3.it/apv/v27/user/login")
                .post(requestBody)
                .build()
        val response = client.newCall(request).execute()
        val body = response.body
        if (body != null) return body.string()
        return "EMPTY BODY RESPONSE"
    }

    /* http post request to get the proxy server url for a given device ID */
    @Throws(IOException::class)
    private fun getProxy(token : String, id: String, secret : String): String {

        var client = OkHttpClient()

        /* google depreciated an 'easy' way to get device public IP, 0.0.0.0 tells api not to discriminate on ip */
        val ip = "0.0.0.0"

        val json = "{ \"deviceaddress\" : \"" + id + "\", \"wait\" : \"true\", \"hostip\" : \"" + ip + "\" }"
        val requestBody = json.toRequestBody(mediaType)
        val request = Request.Builder()
                .header("developerkey", secret)
                .header("token", token)
                .url("https://api.remot3.it/apv/v27/device/connect")
                .post(requestBody)
                .build()
        val response = client.newCall(request).execute()
        val body = response.body
        if (body != null) return body.string()
        return "EMPTY BODY RESPONSE"
    }

    @Throws(IOException::class)
    fun sendArr(data: ByteArray) : String {

        // sends data to remote server
        remoteServer.outputStream.write(data)

        // gets response and returns result
        var response = ""
        val reader = BufferedReader(InputStreamReader(remoteServer.getInputStream()))
        if (reader.ready()) response = reader.readLine()
        return response
    }

}