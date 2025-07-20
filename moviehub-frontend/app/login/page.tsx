"use client"
import {useEffect, useState} from "react";


export default function loginPage() {
    const posters = [
        {
            title: "Interstellar",
            subtitle: "Christopher Nolan · 2014",
            src: "/posters/interstellar.jpg",
            bg: "bg-gradient-to-br from-amber-700 via-gray-900 to-black/40"
        },
        {
            title: "Dune: Part Two",
            subtitle: "Denis Villeneuve · 2024",
            src: "/posters/Dune.jpeg",
            bg: "bg-gradient-to-br from-amber-900/40 to-yellow-800/40"
        },
        {
            title: "Oppenheimer",
            subtitle: "Christopher Nolan · 2023",
            src: "/posters/oppenheimer.jpg",
            bg: "bg-gradient-to-br from-red-900/40 to-orange-900/40"
        },
        {
            title: "The Worst Person in the World",
            subtitle: "Joachim Trier · 2022",
            src: "/posters/worst.jpg",
            bg: "bg-gradient-to-br from-rose-500 via-purple-900 to-gray-900"
        }
    ];
    const[currentPosterIndex, setPosterIndex] = useState(0);// 0 here initializes currentPosterIndex
    useEffect(() => {
        const interval = setInterval(() => {
            setPosterIndex((prevIndex) =>
                (prevIndex + 1) % posters.length);
        }, 5000)
        return () => clearInterval(interval)
    }, []);

    return (
        <div className="flex min-h-screen overflow-hidden"> {/* Added overflow-hidden here */}
            {/* Left Panel - Login Form */}
            <div className="w-full md:w-[55%] bg-black text-white">
                <h1 className="text-3xl font-bold p-8">Log In</h1>
                {/* Login Form */}
            </div>

            {/* Right Panel - Poster Carousel */}
            <div className="hidden md:block w-[45%] relative">
                <div className="absolute inset-0 overflow-hidden">
                    {posters.map((poster, index) => (
                        <div
                            key={index}
                            className={`absolute inset-0 transition-opacity duration-1000 flex items-center justify-center ${poster.bg} ${ // justify centers it horizontally and item centers it vertically.
                                index === currentPosterIndex ? "opacity-100" : "opacity-0"
                            }`}>
                            <div className="absolute inset-0 flex items-center justify-center">
                                <img
                                    src={poster.src}
                                    alt={poster.title + " - " + poster.subtitle}
                                    className="max-w-[95%] max-h-[95%] object-contain rounded-lg shadow-2xl"
                                />
                            </div>

                            <div className="absolute bottom-4 flex justify-center w-full space-x-1">
                                {posters.map((_, i) => (
                                    <div
                                        key={i}
                                        className={`w-2 h-2 rounded-full transition-all duration-300 ${
                                            i === currentPosterIndex ? "bg-white w-6" : "bg-white/30"
                                        }`}
                                    />
                                ))}
                            </div>
                        </div>
                    ))}
                    <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent pointer-events-none" />
                </div>
            </div>
        </div>
    );
}