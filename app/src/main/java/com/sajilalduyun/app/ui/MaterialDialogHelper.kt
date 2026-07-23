package com.sajilalduyun.app.ui

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.actions.setActionButtonEnabled

object MaterialDialogHelper {
    fun showConfirmDialog(
        activity: AppCompatActivity,
        title: String,
        message: String,
        positiveText: String = "تأكيد",
        negativeText: String = "إلغاء",
        onPositive: () -> Unit,
        onNegative: (() -> Unit)? = null
    ) {
        MaterialDialog(activity).show {
            title(text = title)
            message(text = message)
            positiveButton(text = positiveText) {
                onPositive()
            }
            negativeButton(text = negativeText) {
                onNegative?.invoke()
            }
        }
    }

    fun showErrorDialog(
        activity: AppCompatActivity,
        title: String = "خطأ",
        message: String,
        buttonText: String = "حسناً",
        onDismiss: (() -> Unit)? = null
    ) {
        MaterialDialog(activity).show {
            title(text = title)
            message(text = message)
            positiveButton(text = buttonText) {
                onDismiss?.invoke()
            }
        }
    }

    fun showSuccessDialog(
        activity: AppCompatActivity,
        title: String,
        message: String,
        buttonText: String = "حسناً",
        onDismiss: (() -> Unit)? = null
    ) {
        MaterialDialog(activity).show {
            title(text = title)
            message(text = message)
            positiveButton(text = buttonText) {
                onDismiss?.invoke()
            }
        }
    }

    fun showDeleteDialog(
        activity: AppCompatActivity,
        itemName: String,
        onConfirm: () -> Unit
    ) {
        MaterialDialog(activity).show {
            title(text = "حذف $itemName")
            message(text = "هل أنت متأكد من حذف هذا العنصر؟ لا يمكن التراجع عن هذا الإجراء.")
            negativeButton(text = "إلغاء")
            positiveButton(text = "حذف") {
                onConfirm()
            }
        }
    }
}
