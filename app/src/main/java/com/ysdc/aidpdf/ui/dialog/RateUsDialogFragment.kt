package com.ysdc.aidpdf.ui.dialog

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.net.toUri
import com.google.android.play.core.review.ReviewManagerFactory
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.databinding.DialogRateUsBinding
import com.ysdc.aidpdf.store.lastRateShowTime
import com.ysdc.aidpdf.store.rateValue
import com.ysdc.aidpdf.ui.MainActivity
import com.ysdc.aidpdf.ui.basic.BaseDialogFragment

class RateUsDialogFragment : BaseDialogFragment<DialogRateUsBinding>(DialogRateUsBinding::inflate) {

    override val dialogWidthRatio: Float = 0.8f
    var currentRating = 5f
    private lateinit var context: Context
    private lateinit var mainActivity: MainActivity

    override fun setupViews(savedInstanceState: Bundle?) {
        lastRateShowTime = System.currentTimeMillis()
        context = requireContext()
        mainActivity = activity as MainActivity
        applyRating(currentRating)
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.8f)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
    }

    override fun bindActions() {
        binding.tvCancel.setOnClickListener { dismiss() }
        binding.okButton.setOnClickListener {
            rateValue = 1
            if (currentRating < 4.5f) {
                openFeedbackEmail()
                dismiss()
            } else {
                launchInAppReview()
                dismiss()
            }
        }
    }

    fun applyRating(rating: Float) {
        currentRating = rating
        updateRateDialogState(binding, rating)
    }

    private fun updateRateDialogState(binding: DialogRateUsBinding, rating: Float) {
        binding.titleText.text = if (rating < 4.5f) {
            getString(R.string.have_suggestions)
        } else {
            getString(R.string.we_glad)
        }

        val stars = listOf(
            binding.rateStar1,
            binding.rateStar2,
            binding.rateStar3,
            binding.rateStar4,
            binding.rateStar5
        )
        stars.forEachIndexed { index, imageView ->
            when {
                rating >= index + 1 -> imageView.setImageResource(R.mipmap.heart_all)
                rating >= index + 0.5f -> imageView.setImageResource(R.mipmap.heart_part)
                else -> imageView.setImageResource(R.mipmap.heart_no)
            }
            imageView.animate().cancel()
            imageView.scaleX = 1f
            imageView.scaleY = 1f
            imageView.setOnClickListener {
                val targetRating = index + 1f
                val selectedRating = when {
                    rating >= targetRating -> targetRating - 0.5f
                    rating >= targetRating - 0.5f -> targetRating
                    else -> targetRating
                }.coerceIn(0.5f, 5f)
                currentRating = selectedRating
                updateRateDialogState(binding, selectedRating)
            }
        }

        val activeStars = rating.toInt()
        val activeHalf = rating - activeStars >= 0.5f
        stars.forEachIndexed { index, imageView ->
            val isFull = index < activeStars
            val isHalf = index == activeStars && activeHalf
            imageView.translationY = 0f
            imageView.scaleX = when {
                isFull -> 1f
                isHalf -> 1.08f
                else -> 0.92f
            }
            imageView.scaleY = imageView.scaleX
            imageView.alpha = when {
                isFull || isHalf -> 1f
                else -> 0.55f
            }
        }

        binding.rateContainer.animate().cancel()
        binding.rateContainer.alpha = 1f
    }

    private fun openFeedbackEmail() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:upstreamstudio365@gmail.com".toUri()
                putExtra(Intent.EXTRA_EMAIL, arrayOf("upstreamstudio365@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject))
                putExtra(Intent.EXTRA_TEXT, getString(R.string.feedback_body))
            }

//            appInstance.toReStartApp = true
//            app.isNeedJudgeReStartApp = true
            startActivity(Intent.createChooser(intent, getString(R.string.send_feedback)))
        } catch (e: Throwable) {
            Toast.makeText(context, getString(R.string.no_email_app), Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchInAppReview() {
        val ctx = context
        val thanksText = ctx.getString(R.string.thanks_support)
        try {
            val manager = ReviewManagerFactory.create(ctx)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    runCatching {
                        manager.launchReviewFlow(mainActivity, task.result).addOnCompleteListener {
                            Toast.makeText(ctx, thanksText, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val packageName = mainActivity.packageName
                    val marketIntent =
                        Intent(
                            Intent.ACTION_VIEW,
                            "market://details?id=$packageName".toUri()
                        )
                    try {
                        ctx.startActivity(marketIntent)
                    } catch (_: Exception) {
                        ctx.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://play.google.com/store/apps/details?id=$packageName".toUri()
                            )
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e("TAG", "launchInAppReview: $t")
        }
    }
}
