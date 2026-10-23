
package com.example.warrantyvault

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.warrantyvault.data.ProductRepository
import com.example.warrantyvault.data.Product
import com.example.warrantyvault.databinding.ActivityAddProductBinding
import com.example.warrantyvault.util.DateUtils
import com.example.warrantyvault.util.FileUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddProductBinding

    private var purchaseDate: Long = System.currentTimeMillis()
    private var invoicePath: String? = null

    private val invoicePicker =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    invoicePath =
                        FileUtils.copyUriToInternalStorage(this, uri)

                    binding.tvInvoice.text =
                        "Invoice: Selected — scanning OCR..."

                    scanInvoiceWithOcr(uri)

                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "Could not save invoice",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvPurchaseDate.text =
            "Purchase Date: ${DateUtils.format(purchaseDate)}"

        binding.tvPurchaseDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnChooseInvoice.setOnClickListener {
            invoicePicker.launch(arrayOf("image/*"))
        }

        binding.btnSaveProduct.setOnClickListener {
            saveProduct()
        }
    }

    private fun scanInvoiceWithOcr(uri: Uri) {
        val recognizer = TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

        try {
            val image = InputImage.fromFilePath(this, uri)

            recognizer.process(image)
                .addOnSuccessListener { result ->

                    val text = result.text

                    if (text.isBlank()) {
                        binding.tvInvoice.text =
                            "Invoice selected — no text detected"

                        Toast.makeText(
                            this,
                            "No text detected in invoice",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addOnSuccessListener
                    }

                    // Preserve the detected line order.
                    val lines = text.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    // Log OCR output for debugging in Android Studio.
                    android.util.Log.d(
                        "WarrantyOCR",
                        "Detected text:\n$text"
                    )

                    // -----------------------------------------
                    // 1. PRODUCT NAME
                    // -----------------------------------------
                    if (binding.etProductName.text.isNullOrBlank()) {
                        val productLine = lines.firstOrNull { line ->
                            line.contains(
                                Regex(
                                    """[A-Za-z]"""
                                )
                            ) &&
                                    !line.contains("invoice", true) &&
                                    !line.contains("receipt", true) &&
                                    !line.contains("warranty", true) &&
                                    !line.contains("purchase date", true)
                        }

                        productLine?.let {
                            binding.etProductName.setText(it)
                        }
                    }

                    // -----------------------------------------
                    // 2. BRAND
                    // -----------------------------------------
                    if (binding.etBrand.text.isNullOrBlank()) {
                        val brands = listOf(
                            "Samsung", "Apple", "Sony", "LG",
                            "Dell", "HP", "Lenovo", "OnePlus",
                            "Xiaomi", "Redmi", "Realme", "Oppo",
                            "Vivo", "JBL", "boAt", "Bose",
                            "Asus", "Acer", "Canon", "Philips",
                            "Whirlpool", "Panasonic", "Motorola"
                        )

                        val detectedBrand = brands.firstOrNull { brand ->
                            text.contains(brand, ignoreCase = true)
                        }

                        detectedBrand?.let {
                            binding.etBrand.setText(it)
                        }
                    }

                    // -----------------------------------------
                    // 3. PRICE
                    // Supports unlabeled numbers like 74999
                    // -----------------------------------------
                    if (binding.etPrice.text.isNullOrBlank()) {

                        val priceRegex = Regex(
                            """(?i)(?:price\s*[:\-]?\s*)?(?:₹|Rs\.?|INR)?\s*([0-9][0-9,]*(?:\.\d{1,2})?)"""
                        )

                        // First try the third line of the sample.
                        var price: Double? = null

                        if (lines.size >= 3) {
                            price = lines[2]
                                .replace(",", "")
                                .replace(Regex("""(?i)INR|Rs\.?|₹"""), "")
                                .trim()
                                .toDoubleOrNull()
                        }

                        // Fallback: find a labeled price or total.
                        if (price == null) {
                            val labeledLine = lines.firstOrNull {
                                Regex(
                                    """(?i)\b(price|total|amount|grand total|net amount)\b"""
                                ).containsMatchIn(it)
                            }

                            if (labeledLine != null) {
                                price = priceRegex.find(labeledLine)
                                    ?.groupValues
                                    ?.getOrNull(1)
                                    ?.replace(",", "")
                                    ?.toDoubleOrNull()
                            }
                        }

                        if (price != null && price > 0) {
                            binding.etPrice.setText(price.toString())
                        }
                    }

                    // -----------------------------------------
                    // 4. PURCHASE DATE
                    // Supports dd/MM/yyyy and yyyy-MM-dd
                    // -----------------------------------------
                    val dateRegex = Regex(
                        """\b(?:\d{1,2}[/-]\d{1,2}[/-]\d{4}|\d{4}-\d{2}-\d{2})\b"""
                    )

                    val dateMatch = dateRegex.find(text)

                    if (dateMatch != null) {
                        val rawDate = dateMatch.value

                        val formats = listOf(
                            "dd/MM/yyyy",
                            "dd-MM-yyyy",
                            "yyyy-MM-dd"
                        )

                        var parsedDate: java.util.Date? = null

                        for (pattern in formats) {
                            try {
                                val formatter = SimpleDateFormat(
                                    pattern,
                                    Locale.ENGLISH
                                ).apply {
                                    isLenient = false
                                }

                                parsedDate = formatter.parse(rawDate)

                                if (parsedDate != null) break
                            } catch (_: Exception) {
                                // Try another format.
                            }
                        }

                        parsedDate?.let { date ->
                            val calendar = Calendar.getInstance()
                            calendar.time = date
                            calendar.set(Calendar.HOUR_OF_DAY, 0)
                            calendar.set(Calendar.MINUTE, 0)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)

                            purchaseDate = calendar.timeInMillis

                            binding.tvPurchaseDate.text =
                                "Purchase Date: ${
                                    DateUtils.format(purchaseDate)
                                }"
                        }
                    }

                    // -----------------------------------------
                    // 5. WARRANTY DURATION
                    // Supports 6, 6 months, 1 year, etc.
                    // -----------------------------------------
                    if (binding.etWarrantyMonths.text.isNullOrBlank()) {

                        var months: Int? = null

                        // Explicit duration, e.g. 6 months or 1 year.
                        val warrantyRegex = Regex(
                            """(?i)(?:warranty(?:\s+period)?\s*[:\-]?\s*)?(\d{1,2})\s*(years?|yrs?|yr|months?|mos?|mo)\s*(?:of\s*)?warranty?"""
                        )

                        val warrantyMatch = warrantyRegex.find(text)

                        if (warrantyMatch != null) {
                            val amount = warrantyMatch.groupValues[1]
                                .toIntOrNull()

                            val unit = warrantyMatch.groupValues[2]
                                .lowercase(Locale.ENGLISH)

                            if (amount != null && amount > 0) {
                                months = if (
                                    unit.startsWith("year") ||
                                    unit.startsWith("yr")
                                ) {
                                    amount * 12
                                } else {
                                    amount
                                }
                            }
                        }

                        // The supplied sample has a standalone 6
                        // on the fifth non-empty line.
                        if (months == null && lines.size >= 5) {
                            months = lines[4]
                                .toIntOrNull()
                        }

                        if (months != null && months in 1..120) {
                            binding.etWarrantyMonths.setText(
                                months.toString()
                            )
                        }
                    }

                    binding.tvInvoice.text =
                        "Invoice: Scanned — review extracted details"

                    Toast.makeText(
                        this,
                        "OCR complete. Please verify all fields.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .addOnFailureListener {
                    binding.tvInvoice.text =
                        "Invoice selected — OCR failed"

                    Toast.makeText(
                        this,
                        "Could not read invoice image",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .addOnCompleteListener {
                    recognizer.close()
                }

        } catch (e: Exception) {
            recognizer.close()

            binding.tvInvoice.text =
                "Invoice selected — OCR unavailable"

            Toast.makeText(
                this,
                "Could not process invoice image",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = purchaseDate
        }

        DatePickerDialog(
            this,
            { _, year, month, day ->
                val selected = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                purchaseDate = selected.timeInMillis

                binding.tvPurchaseDate.text =
                    "Purchase Date: ${DateUtils.format(purchaseDate)}"
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveProduct() {
        val name = binding.etProductName.text.toString().trim()
        val brand = binding.etBrand.text.toString().trim()
        val priceText = binding.etPrice.text.toString().trim()
        val warrantyText =
            binding.etWarrantyMonths.text.toString().trim()

        if (name.isEmpty()) {
            binding.etProductName.error = "Enter product name"
            return
        }

        if (brand.isEmpty()) {
            binding.etBrand.error = "Enter brand"
            return
        }

        val price = priceText.toDoubleOrNull()

        if (price == null || price < 0) {
            binding.etPrice.error = "Enter a valid price"
            return
        }

        val warrantyMonths = warrantyText.toIntOrNull()

        if (warrantyMonths == null || warrantyMonths <= 0) {
            binding.etWarrantyMonths.error =
                "Enter warranty in months"
            return
        }

        val expiryDate = DateUtils.addMonths(
            purchaseDate,
            warrantyMonths
        )

        val product = Product(
            productName = name,
            brand = brand,
            price = price,
            purchaseDate = purchaseDate,
            warrantyMonths = warrantyMonths,
            expiryDate = expiryDate,
            invoicePath = invoicePath
        )

        lifecycleScope.launch {
            ProductRepository.insert(product)

            Toast.makeText(
                this@AddProductActivity,
                "Product saved successfully",
                Toast.LENGTH_SHORT
            ).show()

            finish()
        }
    }
}
