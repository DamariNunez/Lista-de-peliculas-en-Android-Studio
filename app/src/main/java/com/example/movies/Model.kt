package com.example.movies

import androidx.core.content.ContextCompat.startActivity
import androidx.datastore.core.DataStore
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.e
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import es.viu.retrofitapp.DisneyInterface
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException

class Model(private var moviesDataStore: DataStore<MovieStore>, private val disneyService: DisneyInterface) {

    companion object {
        lateinit var MOVIES: List<Movie>
    }

    private val coroutineScope = CoroutineScope(Job() + Dispatchers.Default)

    private val _movies = MutableStateFlow<List<Movie>>(listOf())
    val movies = _movies as StateFlow<List<Movie>>

    init {

        coroutineScope.launch {
            moviesDataStore.data
                .map { it.initialized }
                .filter { !it }
                .first {
                    d { "Initialize data store..." }
                    try {
                        MOVIES = disneyService.loadMovies()
                        d { "Movies actually downloaded now: $MOVIES" }
                    } catch (e: HttpException) {
                        e(e)
                    }
                    initDataStore()
                    return@first true
                }
        }

        coroutineScope.launch {
            moviesDataStore.data
                .collect { movieStore ->
                    d { "movies count: ${movieStore.moviesCount}" }
                    val movies = movieStore.moviesList.map {
                        Movie(it.id, it.name, it.release, it.playtime, it.description, it.imageUrl)
                    }
                    _movies.value = movies
                }
        }
    }

    private fun initDataStore() {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val type = Types.newParameterizedType(List::class.java, Movie::class.java)
        val adapter = moshi.adapter<List<Movie>>(type)

        val moviesFromJson: List<Movie> = Model.MOVIES!!

        val moviesToStore = moviesFromJson.map { it.asStoredMovie() }

        coroutineScope.launch {
            moviesDataStore.updateData { movieStore ->
                movieStore.toBuilder()
                    .addAllMovies(moviesToStore)
                    .setInitialized(true)
                    .build()
            }
        }
    }

    fun removeMovie(movie: Movie) {
        val toStoreMovies = movies.value
            .filter { it.id != movie.id }
            .map { it.asStoredMovie() }

        coroutineScope.launch {
            moviesDataStore.updateData { movieStore ->
                movieStore.toBuilder()
                    .clearMovies()
                    .addAllMovies(toStoreMovies)
                    .build()
            }
        }
    }
}