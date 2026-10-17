package com.hashem.sms;
interface SubscriptionChoiceCallback { void onSelected(int subscriptionId); default void onCancelled(){} }
