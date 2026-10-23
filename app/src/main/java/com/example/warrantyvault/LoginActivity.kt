
package com.example.warrantyvault

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)

        etEmail.setText(
            intent.getStringExtra("verification_email") ?: ""
        )

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(
                    this,
                    "Enter email and password",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Checking..."

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->

                    if (!task.isSuccessful) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Login"

                        Toast.makeText(
                            this,
                            task.exception?.localizedMessage ?: "Login failed",
                            Toast.LENGTH_LONG
                        ).show()
                        return@addOnCompleteListener
                    }

                    val user = auth.currentUser

                    if (user == null) {
                        auth.signOut()
                        btnLogin.isEnabled = true
                        btnLogin.text = "Login"
                        return@addOnCompleteListener
                    }

                    user.reload().addOnCompleteListener(this) { reloadTask ->
                        val refreshedUser = auth.currentUser

                        if (reloadTask.isSuccessful &&
                            refreshedUser?.isEmailVerified == true
                        ) {
                            startActivity(
                                Intent(this, MainActivity::class.java)
                            )
                            finish()
                        } else {
                            if (refreshedUser != null) {
                                refreshedUser.sendEmailVerification()
                                    .addOnCompleteListener(this) { emailTask ->
                                        auth.signOut()

                                        btnLogin.isEnabled = true
                                        btnLogin.text = "Login"

                                        val message =
                                            if (emailTask.isSuccessful) {
                                                "Email not verified. A new " +
                                                        "verification link has been sent."
                                            } else {
                                                "Email not verified. Check your " +
                                                        "inbox for the verification link."
                                            }

                                        Toast.makeText(
                                            this,
                                            message,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            } else {
                                auth.signOut()
                                btnLogin.isEnabled = true
                                btnLogin.text = "Login"
                            }
                        }
                    }
                }
        }

        tvRegister.setOnClickListener {
            startActivity(
                Intent(this, RegisterActivity::class.java)
            )
        }
    }

    override fun onStart() {
        super.onStart()

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user != null) {
            user.reload().addOnCompleteListener(this) { task ->
                val refreshedUser = auth.currentUser

                if (task.isSuccessful &&
                    refreshedUser?.isEmailVerified == true
                ) {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                    )
                    finish()
                } else {
                    auth.signOut()
                }
            }
        }
    }
}
