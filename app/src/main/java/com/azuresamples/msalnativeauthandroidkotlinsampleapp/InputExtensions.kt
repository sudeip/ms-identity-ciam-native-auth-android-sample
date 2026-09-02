package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Spinner

/**
 * Live-formats phone input as (XXX) XXX-XXXX while typing, same as the web app's onChange handler
 * in ReservationSignup.jsx/ProfileCompletion.jsx (formatPhoneInput in phoneUtils.js).
 */
fun EditText.formatPhoneAsTyped() {
    addTextChangedListener(object : TextWatcher {
        private var isFormatting = false

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable?) {
            if (isFormatting) return
            val original = s?.toString().orEmpty()
            val formatted = ValidationUtils.formatPhoneInput(original)
            if (formatted == original) return

            isFormatting = true
            setText(formatted)
            setSelection(formatted.length.coerceAtMost(length()))
            isFormatting = false
        }
    })
}

/**
 * The selected DOB spinner option as an Int, or null when only the "DD"/"MM"/"YYYY" placeholder
 * at position 0 is selected (ValidationUtils.DAY_OPTIONS/MONTH_OPTIONS/YEAR_OPTIONS).
 */
fun Spinner.selectedOptionOrNull(): Int? {
    if (selectedItemPosition <= 0) return null
    return (selectedItem as? String)?.toIntOrNull()
}
