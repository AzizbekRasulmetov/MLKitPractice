package com.aziz.facedetectiontest.text_reader

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aziz.facedetectiontest.databinding.ActivityMyCardBinding


class MyCardActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMyCardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMyCardBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        intent.apply {
            viewBinding.numberTxt.text = "Card number: ${this.getStringExtra("cardNumber")}"
            viewBinding.dateTxt.text = "Expiry date: ${this.getStringExtra("expiryDate")}"
        }
    }

}