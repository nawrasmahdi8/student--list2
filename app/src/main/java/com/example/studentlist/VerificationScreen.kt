package com.example.studentlist

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import kotlinx.coroutines.launch
import java.net.URL
import java.util.UUID
import kotlin.random.Random

@Composable
fun VerificationScreen(context: Context) {
    var phoneNumber by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var isCodeSent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var sentCode by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "تحقق من هويتك",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (!isCodeSent) {
            TextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("رقم الهاتف") },
                placeholder = { Text("+964XXXXXXXXX") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )

            Button(
                onClick = {
                    if (phoneNumber.isBlank()) {
                        Toast.makeText(context, "أدخل رقم الهاتف", Toast.LENGTH_SHORT).show()
                    } else {
                        isLoading = true
                        scope.launch {
                            sendVerificationCode(
                                phoneNumber,
                                context,
                                onSuccess = { code ->
                                    sentCode = code
                                    isCodeSent = true
                                    isLoading = false
                                    Toast.makeText(context, "تم إرسال الرمز", Toast.LENGTH_SHORT).show()
                                },
                                onError = {
                                    isLoading = false
                                    Toast.makeText(context, "خطأ: $it", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("أرسل الرمز")
                }
            }
        } else {
            Text(
                text = "تم إرسال رمز التحقق إلى $phoneNumber",
                modifier = Modifier.padding(bottom = 24.dp)
            )

            TextField(
                value = verificationCode,
                onValueChange = { verificationCode = it },
                label = { Text("رمز التحقق") },
                placeholder = { Text("000000") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )

            Button(
                onClick = {
                    if (verificationCode == sentCode) {
                        Toast.makeText(context, "✅ تم التحقق بنجاح!", Toast.LENGTH_SHORT).show()
                        saveUserToDatabase(phoneNumber)
                    } else {
                        Toast.makeText(context, "❌ الرمز غير صحيح", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("تحقق")
            }

            TextButton(
                onClick = {
                    isCodeSent = false
                    verificationCode = ""
                   // phoneNumber = ""
                    sentCode = ""
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("استخدم رقماً آخر")
            }
        }
    }
}

fun sendVerificationCode(
    phoneNumber: String,
    context: Context,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val code = String.format("%06d", Random.nextInt(1000000))
        
        val url = URL("https://pvdghgrvxcxpxekvtybx.supabase.co/functions/v1/send-verification-whatsapp")
        val connection = url.openConnection() as java.net.HttpURLConnection
        
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val jsonBody = """
            {
                "phoneNumber": "$phoneNumber",
                "verificationCode": "$code"
            }
        """.trimIndent()

        connection.outputStream.use { os ->
            os.write(jsonBody.toByteArray())
            os.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode == 200) {
            Log.d("Verification", "رمز التحقق: $code")
            onSuccess(code)
        } else {
            val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "خطأ غير معروف"
            onError(errorStream)
        }
        
        connection.disconnect()
    } catch (e: Exception) {
        onError(e.message ?: "خطأ في الاتصال")
    }
}

fun saveUserToDatabase(phoneNumber: String) {
    try {
        val url = URL("https://pvdghgrvxcxpxekvtybx.supabase.co/rest/v1/users")
        val connection = url.openConnection() as java.net.HttpURLConnection
        
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        
        // Security: Avoid hardcoding API keys. Use BuildConfig via Secrets panel.
        // Replace with BuildConfig.SUPABASE_ANON_KEY
        val apiKey = BuildConfig.SUPABASE_ANON_KEY
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.doOutput = true

        val jsonBody = """
            {
                "id": "${UUID.randomUUID()}",
                "phone": "$phoneNumber",
                "is_verified": true
            }
        """.trimIndent()

        connection.outputStream.use { os ->
            os.write(jsonBody.toByteArray())
            os.flush()
        }

        val responseCode = connection.responseCode
        Log.d("Database", "حفظ المستخدم: $responseCode")
        
        connection.disconnect()
    } catch (e: Exception) {
        Log.e("Database", "خطأ: ${e.message}")
    }
}
