package com.example.weatherforecast

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.HandlerCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object{
        private const val DEBUG_TAG="AsyncSample"
        private const val WEATHERINFO_URL = "https://api.openweathermap.org/data/2.5/weather?lang=ja"
        private const val APP_ID = "4b9ad6c46069a933bd78040049d6b572"
    }
    private var _list: MutableList<MutableMap<String, String>> = mutableListOf()

    override fun onCreate(saveInstanceState: Bundle?) {
        super.onCreate(saveInstanceState)
        setContentView(R.layout.activity_main)

        _list = createList()

        val lvCityList = findViewById<ListView>(R.id.lvCityList)
        val from=arrayOf("name")
        val to=intArrayOf(android.R.id.text1)
        val adapter = SimpleAdapter(this@MainActivity, _list, android.R.layout.simple_list_item_1, from, to)
        lvCityList.adapter = adapter
        lvCityList.onItemClickListener = ListItemClickListener()
    }

	/**
	 * リストビューに表示させる天気ポイントリストデータを生成する
	 *
	 * @return 生成された天気ポイントリストデータ。
	 */
    private fun createList(): MutableList<MutableMap<String, String>> {
        var list: MutableList<MutableMap<String, String>> = mutableListOf()

        var city = mutableMapOf("name" to "新宿","q" to "Shinjuku")
        list.add(city)
        city = mutableMapOf("name" to "渋谷","q" to "Shibuya")
        list.add(city)
        city = mutableMapOf("name" to "池袋","q" to "Ikebukuro")
        list.add(city)
        city = mutableMapOf("name" to "横浜","q" to "Yokohama")
        list.add(city)
        city = mutableMapOf("name" to "川崎","q" to "Kawasaki")
        list.add(city)
        city = mutableMapOf("name" to "名古屋","q" to "Nagoya")
        list.add(city)

        return list
    }

	/**
	 * お天気情報の取得処理を行う
	 *
	 * @param url お天気情報を取得するURL
	 */
    @UiThread
    private fun receiveWeatherInfo(urlFull: String) {
        val handler = HandlerCompat.createAsync(mainLooper)
        val backgroundReceiver = WeatherInfoBackgroundReceiver(handler, urlFull)
        val executeService = Executors.newSingleThreadExecutor()
        executeService.submit(backgroundReceiver)
    }

	/**
	 * お天気情報の取得処理を行う
	 *
	 * @param url お天気情報を取得するURL
	 */
    private inner class WeatherInfoBackgroundReceiver(handler: Handler, url: String): Runnable {
        private val _handler = handler
        private val _url = url

        @WorkerThread
        override fun run() {
            var result = ""
            val url = URL(_url)
            val con = url.openConnection() as? HttpURLConnection
            con?.let {
                try {
                    it.connectTimeout = 1000
                    it.readTimeout = 1000
                    it.requestMethod = "GET"
                    it.connect()
                    val stream = it.inputStream
                    result = is2String(stream)
                    stream.close()
                }
                catch(ex: SocketTimeoutException) {
                    Log.w(DEBUG_TAG, "通信タイムアウト", ex)
                }
                it.disconnect()
            }
            val postExecutor = WeatherInfoPostExecutor(result)
            _handler.post(postExecutor)
        }

		/**
		 * InputStreamオブジェクトを文字列に変換する
		 *
		 * @param stream 変換対象のInputStreamオブジェクト
		 * @return 変換された文字列
		 */
        private fun is2String(stream: InputStream): String {
            val sb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
            var line = reader.readLine()
            while(line != null) {
                sb.append(line)
                line = reader.readLine()
            }
            reader.close()
            return sb.toString()
        }
    }

	/**
	 * 非同期でお天気情報を取得した後にUIスレッドでその情報を表示する
	 *
	 * @param result Web APIから取得したお天気情報JSON文字列
	 */
    private inner class WeatherInfoPostExecutor(result: String): Runnable {
        private val _result = result

        @UiThread
        override fun run() {
            val rootJSON = JSONObject(_result)
            val cityName = rootJSON.getString("name")
            val coordJSON = rootJSON.getJSONObject("coord")
            val latitude = coordJSON.getString("lat")
            val longitude = coordJSON.getString("lon")
            val weatherJSONArray = rootJSON.getJSONArray("weather")
            val weatherJSON = weatherJSONArray.getJSONObject(0)
            val weather = weatherJSON.getString("description")
            val telop = "${cityName}の天気"
            val desc = "現在は${weather}です。\n緯度は${latitude}度で経度は${longitude}度です。"
            val tvWeatherTelop = findViewById<TextView>(R.id.tvWeatherTelop)
            val tvWeatherDesc = findViewById<TextView>(R.id.tvWeatherDesc)
            tvWeatherTelop.text = telop
            tvWeatherDesc.text = desc
        }
    }

	/**
	 * リストがタップされた時の処理
	 */
    private inner class ListItemClickListener: AdapterView.OnItemClickListener {
        override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
            val item = _list.get(position)
            val q = item.get("q")
            q?.let {
                val urlFull = "$WEATHERINFO_URL&q=$q&appid=$APP_ID"
                receiveWeatherInfo(urlFull)
            }
        }
    }
}
