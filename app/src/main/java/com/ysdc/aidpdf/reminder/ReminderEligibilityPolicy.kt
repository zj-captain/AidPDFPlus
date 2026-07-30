package com.ysdc.aidpdf.reminder

import android.content.Context
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.core.block.BlockUtils.SamsungCategory

object ReminderEligibilityPolicy {

    fun canSend(context: Context): Boolean {
        return BlockUtils.samsungCategory() != SamsungCategory.Korea &&
            !BlockUtils.shouldBlockAds(context)
    }
}
