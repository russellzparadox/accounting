package com.example.accounting.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChartOfAccountsTest {
    private val chart = ChartOfAccounts.standard()

    @Test
    fun loadsAllAccountsFromResource() {
        assertEquals(34, chart.codes.size)
    }

    @Test
    fun everyTemplateAccountCodeExists() {
        val templateCodes = listOf("1100", "1200", "1201", "1300", "2100", "3100", "3200", "4100", "4200", "5100", "5200") +
            ExpenseCategory.entries.map { it.accountCode }
        templateCodes.forEach { assertEquals(true, it in chart.codes, it) }
    }

    @Test
    fun bankControlHasOneDetailAccount() {
        assertEquals(listOf("1201"), chart.childrenOf("1200").map { it.code })
    }

    @Test
    fun groupAndControlWithChildrenAreNotPostable() {
        assertFailsWith<DomainException> { chart.requirePostable("1000") }
        assertFailsWith<DomainException> { chart.requirePostable("1200") }
        chart.requirePostable("1201")
        chart.requirePostable("1100")
    }

    @Test
    fun rejectsDuplicateCodes() {
        val json = """{"schema_version":1,"accounts":[
            {"code":"1000","name_fa":"a","level":"گروه","type":"asset","normal_balance":"debit"},
            {"code":"1000","name_fa":"b","level":"گروه","type":"asset","normal_balance":"debit"}]}"""
        assertFailsWith<IllegalArgumentException> { ChartOfAccounts.fromJson(json) }
    }

    @Test
    fun rejectsUnknownParent() {
        val json = """{"schema_version":1,"accounts":[
            {"code":"1100","name_fa":"a","level":"کل","parent":"1000","type":"asset","normal_balance":"debit"}]}"""
        assertFailsWith<IllegalArgumentException> { ChartOfAccounts.fromJson(json) }
    }
}
