package com.example.weatherforecast

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class MainActivity : AppCompatActivity() {
    companion object{
        private const val DEBUG_TAG="AsyncSample"
        private const val WEATHERINFO_URL = "https://api.openweathermap.org/data/2.5/weather?lang=ja"
        private const val APP_ID = "4b9ad6c46069a933bd78040049d6b572"
    }
    private var _list: MutableList<MutableMap<String, String>> = mutableListOf()
    private val _helper = DatabaseHelper(this@MainActivity)
    private var _cityId = -1
    private var _cityName = ""


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

    override fun onDestroy() {
        _helper.close()
        super.onDestroy()
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
        city = mutableMapOf("name" to "横浜","q" to "Yokohama")
        list.add(city)
        city = mutableMapOf("name" to "名古屋","q" to "Nagoya")
        list.add(city)
        city = mutableMapOf("name" to "札幌","q" to "Sapporo")
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
        lifecycleScope.launch {
            val result = weatherInfoBackgroundRunner(urlFull)
            weatherInfoPostRunner(result)
        }
    }

	/**
	 * お天気情報の取得処理を行う
	 *
	 * @param url お天気情報を取得するURL
	 */
    @WorkerThread
    private suspend fun weatherInfoBackgroundRunner(url: String): String {
        val returnVal = withContext(Dispatchers.IO) {
            var result = ""
            val url = URL(url)
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
                catch (ex: SocketTimeoutException) {
                    Log.w(DEBUG_TAG, "通信タイムアウト", ex)
                }
                it.disconnect()
            }
            result
        }
        return returnVal
    }

    @UiThread
    private fun weatherInfoPostRunner(result: String) {
        val rootJSON = JSONObject(result)
        val coordJSON = rootJSON.getJSONObject("coord")
        val weatherJSONArray = rootJSON.getJSONArray("weather")
        val weatherJSON = weatherJSONArray.getJSONObject(0)
        val weather = weatherJSON.getString("description")
        val desc = "${weather}です。"
        val tvWeatherDesc = findViewById<TextView>(R.id.tvWeatherDesc)
        tvWeatherDesc.text = desc
    }

    /**
     * InputStreamオブジェクトを文字列に変換するメソッド。 変換文字コードはUTF-8。
     *
     * @param stream 変換対象のInputStreamオブジェクト。
     * @return 変換された文字列。
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

	/**
	 * リストがタップされた時の処理
	 */
    private inner class ListItemClickListener: AdapterView.OnItemClickListener {
        override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
            val item = _list.get(position)
            _cityId = position
            val q = item.get("q")
            q?.let {
                val urlFull = "$WEATHERINFO_URL&q=$q&appid=$APP_ID"
                receiveWeatherInfo(urlFull)
            }
            val tvCityName = findViewById<TextView>(R.id.tvCityName)
            tvCityName.text = item.get("Name")
            val btnSave = findViewById<Button>(R.id.btnSave)
            btnSave.isEnabled = true

            val db = _helper.writableDatabase
            val sql = "SELECT*FROM citymemos WHERE_id = ${_cityId}"
            val cursor = db.rawQuery(sql, null)
            var note = ""
            while(cursor.moveToNext()) {
                val idxNote = cursor.getColumnIndex("note")
                note = cursor.getString(idxNote)
            }
            val etNote = findViewById<EditText>(R.id.etNote)
            etNote.setText(note)
        }
    }

    /**
     * 保存ボタンがタップされた時の処理
     */
    fun onSaveButtonClick(view: View) {
        val etNote = findViewById<EditText>(R.id.etNote)
        val note = etNote.text.toString()
        val db = _helper.writableDatabase

        val sqlDelete = "DELETE FROM citymemos WHERE _id = ?"
        var stmt = db.compileStatement(sqlDelete)
        stmt.bindLong(1, _cityId.toLong())
        stmt.executeUpdateDelete()

        val sqlInsert = "INSERT INTO citymemos (_id, name, note) VALUES (?, ?, ?)"
        stmt = db.compileStatement(sqlInsert)
        stmt.bindLong(1, _cityId.toLong())
        stmt.bindString(2, _cityName)
        stmt.bindString(3, note)
        stmt.executeInsert()

        etNote.setText("")
        val tvWeatherDesc = findViewById<TextView>(R.id.tvWeatherDesc)
        tvWeatherDesc.setText("")
        val tvCityName = findViewById<TextView>(R.id.tvCityName)
        tvCityName.text = getString(R.string.tv_name)
        val btnSave = findViewById<Button>(R.id.btnSave)
        btnSave.isEnabled = false
    }
}
