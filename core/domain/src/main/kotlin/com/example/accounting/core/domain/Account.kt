package com.example.accounting.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

@Serializable
enum class AccountLevel {
    @SerialName("گروه") GROUP,
    @SerialName("کل") GENERAL,
    @SerialName("معین") DETAIL,
}

@Serializable
enum class AccountType {
    @SerialName("asset") ASSET,
    @SerialName("liability") LIABILITY,
    @SerialName("equity") EQUITY,
    @SerialName("revenue") REVENUE,
    @SerialName("cogs") COGS,
    @SerialName("expense") EXPENSE,
}

@Serializable
data class Account(
    val code: String,
    @SerialName("name_fa") val nameFa: String,
    val level: AccountLevel,
    val parent: String? = null,
    val type: AccountType,
    @SerialName("normal_balance") val normalBalance: Side,
)

/** Immutable chart of accounts. [standard] loads `chart_of_accounts.json` from resources. */
class ChartOfAccounts private constructor(private val byCode: Map<String, Account>) {
    private val children: Map<String, List<Account>> =
        byCode.values.mapNotNull { acc -> acc.parent?.let { it to acc } }
            .groupBy({ it.first }, { it.second })

    val codes: Set<String> get() = byCode.keys

    fun account(code: String): Account? = byCode[code]

    fun childrenOf(code: String): List<Account> = children[code].orEmpty()

    /** Throws unless [code] is a known کل/معین account with no معین children. */
    fun requirePostable(code: String) {
        val account = byCode[code] ?: throw DomainException("unknown account $code")
        if (account.level == AccountLevel.GROUP) throw DomainException("group account $code is not postable")
        if (childrenOf(code).isNotEmpty()) throw DomainException("account $code needs a detail (معین) account")
    }

    companion object {
        private val codePattern = Regex("[0-9]{4}")

        fun fromJson(text: String): ChartOfAccounts {
            val file = domainJson.decodeFromString<ChartFile>(text)
            require(file.schemaVersion == 1) { "unsupported chart schema ${file.schemaVersion}" }
            val byCode = file.accounts.associateBy { it.code }
            require(byCode.size == file.accounts.size) { "duplicate account code" }
            file.accounts.forEach { acc ->
                require(codePattern.matches(acc.code)) { "account code must be 4 Latin digits: ${acc.code}" }
                require(acc.parent == null || acc.parent in byCode) { "unknown parent of ${acc.code}" }
            }
            return ChartOfAccounts(byCode)
        }

        fun standard(): ChartOfAccounts {
            val stream = requireNotNull(ChartOfAccounts::class.java.getResourceAsStream("/chart_of_accounts.json")) {
                "chart_of_accounts.json missing from resources"
            }
            return fromJson(stream.bufferedReader().use { it.readText() })
        }
    }
}

@Serializable
private class ChartFile(
    @SerialName("schema_version") val schemaVersion: Int,
    val accounts: List<Account>,
)
