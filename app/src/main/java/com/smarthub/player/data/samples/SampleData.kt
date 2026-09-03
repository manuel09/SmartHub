package com.smarthub.player.data.samples

import com.smarthub.player.data.model.*

object SampleData {
    val featuredMovie = Movie(
        id = 1,
        title = "The Boys",
        overview = "Uno sguardo irriverente a ciò che succede quando i supereroi, che sono popolari come le celebrità, influenti come i politici e venerati come dei, abusano dei loro superpoteri invece di utilizzarli a fin di bene.",
        posterPath = "/stTEyS19YvY8CDvXNq4uS2v9Y9G.jpg",
        backdropPath = "/n6B9VvR79Xo1Cqr76C9R96TMz4t.jpg",
        voteAverage = 8.5
    )

    val movies = listOf(
        Movie(2, "The Punisher", null, "Senza pietà...", "/8Y02UuW5P87aT2j5o54y169OQ.jpg", "/68G9y5P87aT2j5o54y169OQ.jpg", null, 8.5),
        Movie(3, "La Mummia", null, "Dalle sabbie...", "/wRyl6Q8eHq0wXFmHjU0YFvI6L.jpg", "/wRyl6Q8eHq0wXFmHjU0YFvI6L.jpg", null, 7.9),
        Movie(4, "Michael", null, "La storia...", "/A6hH9y5P87aT2j5o54y169OQ.jpg", "/A6hH9y5P87aT2j5o54y169OQ.jpg", null, 7.8),
        Movie(5, "Return to Silent Hill", null, "Il terrore...", "/B6hH9y5P87aT2j5o54y169OQ.jpg", "/B6hH9y5P87aT2j5o54y169OQ.jpg", null, 6.5)
    )

    val cast = listOf(
        Cast(1, "Karl Urban", "Billy Butcher", "/6Y02UuW5P87aT2j5o54y169OQ.jpg"),
        Cast(2, "Jack Quaid", "Hughie Campbell", "/7Y02UuW5P87aT2j5o54y169OQ.jpg"),
        Cast(3, "Antony Starr", "Homelander", "/8Y02UuW5P87aT2j5o54y169OQ.jpg"),
        Cast(4, "Erin Moriarty", "Annie January", "/9Y02UuW5P87aT2j5o54y169OQ.jpg")
    )

    val seasons = listOf(
        Season(1, 1, 8, "Stagione 1", "/stTEyS19YvY8CDvXNq4uS2v9Y9G.jpg"),
        Season(2, 2, 8, "Stagione 2", "/stTEyS19YvY8CDvXNq4uS2v9Y9G.jpg"),
        Season(3, 3, 8, "Stagione 3", "/stTEyS19YvY8CDvXNq4uS2v9Y9G.jpg")
    )
}
