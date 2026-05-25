package com.example

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object DeviceHistory {
    private const val PREFS_NAME = "sharelink_prefs"
    private const val KEY_HISTORY = "recent_devices"
    
    fun getRecentDevices(context: Context): List<DeviceInfo> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyStr = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val list = ArrayList<DeviceInfo>()
        try {
            val arr = JSONArray(historyStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(DeviceInfo(
                    name = obj.getString("name"),
                    ip = obj.getString("ip"),
                    httpPort = obj.getInt("http_port"),
                    udpPort = obj.optInt("udp_port", 0)
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
    
    fun saveDevice(context: Context, device: DeviceInfo) {
        val current = getRecentDevices(context).toMutableList()
        current.removeAll { it.ip == device.ip }
        current.add(0, device) // Add to top
        val limited = current.take(10)
        
        val arr = JSONArray()
        for (d in limited) {
            val obj = JSONObject()
            obj.put("name", d.name)
            obj.put("ip", d.ip)
            obj.put("http_port", d.httpPort)
            obj.put("udp_port", d.udpPort)
            arr.put(obj)
        }
        
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, arr.toString())
            .apply()
    }
}
