package com.hashem.sms;
final class MmsSendState {
 private MmsSendState(){}
 static String statusFor(boolean success){return success?"تم الإرسال":"فشل";}
 static boolean canRetry(String status){if(status==null)return false;String s=status.trim().toLowerCase(java.util.Locale.ROOT);return s.contains("fail")||s.contains("فشل")||s.contains("لم يتم");}
}
