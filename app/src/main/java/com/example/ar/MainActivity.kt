package com.example.ar

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initializing Variables
        val tokenEditText: EditText = findViewById(R.id.editTextToken)
        val channelEditText: EditText = findViewById(R.id.editTextChannel)
        val userButton: Button = findViewById(R.id.buttonUser)
        val expertButton: Button = findViewById(R.id.buttonExpert)

        //When 'User' button is clicked
        userButton.setOnClickListener {
            val channelName = channelEditText.text.toString()
            val token = tokenEditText.text.toString()

           Intent(this,DrawingActivity::class.java).also {
                it.putExtra("EXTRA_TOKEN", token)
                it.putExtra("EXTRA_CHANNEL", channelName)
                startActivity(it)
        }
           /* Intent(this, LoginActivity::class.java).also {
                startActivity(it)
                //Intent(this,DrawingActivity::class.java).also { startActivity(it) }
            }*/
    }

        //When 'Expert' button is clicked
        expertButton.setOnClickListener {
            val channelName = channelEditText.text.toString()
            val token = tokenEditText.text.toString()

           Intent(this,ExpertActivity::class.java).also {
                it.putExtra("EXTRA_TOKEN", token)
                it.putExtra("EXTRA_CHANNEL", channelName)
                startActivity(it)
            }
         /*   Intent(this,LoginActivity::class.java).also {
                startActivity(it)
                //Intent(this,DrawingActivity::class.java).also { startActivity(it) }
            }*/
        }
    }
}