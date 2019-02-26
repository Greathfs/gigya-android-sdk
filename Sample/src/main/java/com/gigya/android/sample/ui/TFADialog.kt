package com.gigya.android.sample.ui

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.gigya.android.sample.R
import com.gigya.android.sample.extras.gone
import com.gigya.android.sample.extras.visible
import com.gigya.android.sample.model.CountryCode
import com.gigya.android.sdk.interruption.GigyaResolver
import com.google.gson.Gson
import kotlinx.android.synthetic.main.dialog_tfa_code_input.*
import kotlinx.android.synthetic.main.dialog_tfa_registration.*
import kotlinx.android.synthetic.main.dialog_tfa_verification.*

class TFADialog : DialogFragment() {

    private var viewModel: MainViewModel? = null

    companion object {

        fun newInstance(mode: String, providers: ArrayList<String>?): TFADialog {
            val dialog = TFADialog()
            val args = Bundle()
            args.putString("mode", mode)
            args.putStringArrayList("providers", providers)
            dialog.arguments = args
            return dialog
        }
    }

    private lateinit var mode: String

    private var codes: Array<CountryCode>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = activity?.run {
            ViewModelProviders.of(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")
        mode = arguments!!["mode"] as String
        loadCountryCodes()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val layoutParams = dialog?.window?.attributes
        layoutParams?.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams?.gravity = Gravity.TOP
        layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        dialog?.window?.attributes = layoutParams
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = when (mode) {
            "registration" -> R.layout.dialog_tfa_registration
            "code_input" -> R.layout.dialog_tfa_code_input
            "verification" -> R.layout.dialog_tfa_verification
            else -> 0
        }
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        when (mode) {
            "registration" -> setupForRegistration()
            "code_input" -> setupForCodeInput()
            "verification" -> setupForVerification()
        }
    }

    private fun loadCountryCodes() {
        val json: String = context?.assets?.open("countryCodes.json")?.bufferedReader().use { it!!.readText() }
        codes = Gson().fromJson(json, Array<CountryCode>::class.java)
    }

    private fun setupForRegistration() {
        val providers = arguments!!.getStringArrayList("providers")
        // Populate options spinner.
        val providerAdapter = ArrayAdapter(context!!, android.R.layout.simple_spinner_dropdown_item, providers!!)
        register_provider_spinner.adapter = providerAdapter
        register_provider_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Stub.
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (providers[position] == GigyaResolver.TFA_PHONE) {
                    true -> {
                        method_group.visible()
                        phone_edit.visible()
                        phone_number.visible()
                    }
                    false -> {
                        method_group.gone()
                        phone_edit.gone()
                        phone_number.gone()
                    }
                }
            }
        }

        // Populate country code spinner.
        val codeAdapter = ArrayAdapter(context!!, android.R.layout.simple_spinner_dropdown_item, codes!!)
        country_spinner.adapter = codeAdapter

        register_tfa_send_code.setOnClickListener {
            val country = (country_spinner).selectedItem as CountryCode
            val method = register_provider_spinner.selectedItem.toString()
            when (method) {
                GigyaResolver.TFA_PHONE -> {
                    viewModel?.onTFAPhoneRegister(
                            country.dial_code + phone_edit.text.toString().trim().replace("+", ""),
                            when (method_group.checkedRadioButtonId) {
                                R.id.radio_sms -> "sms"
                                R.id.radio_voice -> "voice"
                                else -> "sms"
                            })
                }
                GigyaResolver.TFA_TOTP -> {

                }
                else -> {
                }
            }
            dismiss()
        }
    }

    private fun setupForCodeInput() {
        code_input_tfa_send_code.setOnClickListener {
            val code = verify_tfa_edit.text.toString().trim()
            viewModel?.onTFAPhoneCodeSubmit(code)
            dismiss()
        }
    }

    private fun setupForVerification() {
        val providers = arguments!!.getStringArrayList("providers")
        // Populate options spinner.
        val providerAdapter = ArrayAdapter(context!!, android.R.layout.simple_spinner_dropdown_item, providers)
        verify_provider_spinner.adapter = providerAdapter
        verify_tfa_send_code.setOnClickListener {
            val provider = verify_provider_spinner.selectedItem.toString()
            when (provider) {
                GigyaResolver.TFA_PHONE -> viewModel?.onTFAPhoneVerify()
                GigyaResolver.TFA_TOTP -> {
                }
            }
            dismiss()
        }
    }
}