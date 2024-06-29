package com.best.finish

import android.annotation.SuppressLint
import android.app.ProgressDialog.show
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.constraintlayout.motion.widget.Debug.getLocation
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.best.finish.databinding.ActivityMainBinding
import com.best.finish.ui.theme.FinishTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class MainActivity :  AppCompatActivity() {
    companion object {
        private const val API_KEY = "4a1b545ae9a24e64e0050d10af14bfe1"
        private const val CITY_NAME_URL = "https://api.openweathermap.org/data/2.5/weather?q="
        private const val GEO_COORDINATES_URL = "https://api.openweathermap.org/data/2.5/weather?lat="
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        binding.fab.setOnClickListener {
            getLocation()
        }

        binding.root.setOnRefreshListener {
            refreshData()
        }
    }

    override fun onResume() {
        super.onResume()

        refreshData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh -> refreshData()
            R.id.change_city -> showInputDialog()
            R.id.language_en -> changeLanguage("en")
            R.id.language_es -> changeLanguage("es")
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) getLocation()
        else if (requestCode == 1) {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
        }
    }

    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(applicationContext,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val url = GEO_COORDINATES_URL + location.latitude + "&lon=" + location.longitude
                    updateWeatherData(url)
                    sharedPreferences.edit().apply {
                        putString("location", "currentLocation")
                        apply()
                    }
                }
            }
        }
    }

    private fun updateWeatherData(url: String) {
        object : Thread() {
            override fun run() {
                val jsonObject = getJSON(url)
                runOnUiThread {
                    if (jsonObject != null) renderWeather(jsonObject)
                    else Toast.makeText(this@MainActivity, getString(R.string.data_not_found), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun getJSON(url: String): JSONObject? {
        try {
            val con = URL("$url&appid=$API_KEY&units=metric").openConnection() as HttpURLConnection
            con.apply {
                doOutput = true
                connect()
            }

            val inputStream = con.inputStream
            val br = BufferedReader(InputStreamReader(inputStream!!))
            var line: String?
            val buffer = StringBuffer()
            while (br.readLine().also { line = it } != null) buffer.append(line + "\n")
            inputStream.close()
            con.disconnect()

            val jsonObject = JSONObject(buffer.toString())

            return if (jsonObject.getInt("cod") != 200) null
            else jsonObject
        } catch (_: Throwable) {
            return null
        }
    }

    @SuppressLint("StringFormatMatches")
    private fun renderWeather(json: JSONObject) {
        try {
            json.getString("name").uppercase(Locale.US)
            json.getJSONObject("sys").getString("country")
            binding.txtCity.text = resources.getString(R.string.city_field, "Madrid", "Spain")

            val weatherDetails = json.optJSONArray("weather")?.getJSONObject(0)
            val main = json.getJSONObject("main")
            val description = weatherDetails?.getString("description")
            val humidity = main.getString("humidity")
            val pressure = main.getString("pressure")
            binding.txtDetails.text = resources.getString(R.string.details_field, description, humidity, pressure)

            // The backup icon is 03d (cloudy) for null results
            // Full list of icons available here https://openweathermap.org/weather-conditions#Weather-Condition-Codes-2
            val iconID = weatherDetails?.getString("icon") ?: "03d"
            val url = "https://openweathermap.org/img/wn/$iconID@2x.png"

            Glide.with(this)
                .load(url)
                .transition(DrawableTransitionOptions.withCrossFade())
                .centerCrop()
                .into(binding.imgWeatherIcon)

            val temperature = main.getDouble("temp")
            binding.txtTemperature.text = resources.getString(R.string.temperature_field, temperature)

            val df = DateFormat.getDateTimeInstance()
            val lastUpdated = df.format(Date(json.getLong("dt") * 1000))
            binding.txtUpdated.text = resources.getString(R.string.updated_field, lastUpdated)
        } catch (_: Exception) { }
    }

    private fun showInputDialog() {
        val input = EditText(this@MainActivity)
        input.inputType = InputType.TYPE_CLASS_TEXT

        AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.change_city))
            setView(input)
            setPositiveButton(getString(R.string.go)) { _, _ ->
                val city = input.text.toString()
                updateWeatherData("$CITY_NAME_URL$city")
                sharedPreferences.edit().apply {
                    putString("location", city)
                    apply()
                }
            }
            show()
        }
    }

    private fun refreshData() {
        binding.root.isRefreshing = true
        when (val location = sharedPreferences.getString("location", null)) {
            null, "currentLocation" -> getLocation()
            else -> updateWeatherData("$CITY_NAME_URL$location")
        }
        binding.root.isRefreshing = false
    }

    private fun changeLanguage(isoCode: String) {
        val appLocale = LocaleListCompat.forLanguageTags(isoCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

}