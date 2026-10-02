package com.playertwo.controlegithub

data class Repository(val name: String, val description: String, val language: String, val stars: Int, val issues: Int)

object DemoData {
    val repositories = listOf(
        Repository("controlegithub", "Seu GitHub, sob controle. Agora no Android.", "Kotlin", 12, 4),
        Repository("ideias_standard", "Uma base simples para construir projetos melhores.", "Python", 28, 2),
        Repository("orbit-design", "Componentes e tokens para interfaces consistentes.", "TypeScript", 64, 7)
    )
    fun search(query: String): List<Repository> = repositories.filter {
        it.name.contains(query.trim(), ignoreCase = true) || it.language.contains(query.trim(), ignoreCase = true)
    }
}
