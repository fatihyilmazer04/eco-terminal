import React from 'react'
import { useMyFlights } from '../../hooks/useFlights'
import FlightCard from '../../components/FlightCard'

function SkeletonCard() {
  return (
    <div className="eco-card animate-pulse">
      <div className="flex items-center justify-between mb-4">
        <div className="h-5 w-24 bg-gray-700 rounded" />
        <div className="h-5 w-16 bg-gray-700 rounded-full" />
      </div>
      <div className="flex items-center gap-3 mb-4">
        <div className="h-10 w-20 bg-gray-700 rounded" />
        <div className="flex-1 h-px bg-gray-700" />
        <div className="h-8 w-28 bg-gray-700 rounded" />
      </div>
      <div className="flex justify-between pt-3 border-t border-gray-700">
        <div className="h-4 w-20 bg-gray-700 rounded" />
        <div className="h-4 w-16 bg-gray-700 rounded" />
        <div className="h-4 w-16 bg-gray-700 rounded" />
      </div>
    </div>
  )
}

export default function FlightInfoPage() {
  const { data: flights, loading, error, refetch } = useMyFlights()

  return (
    <div className="min-h-screen bg-gray-900 p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white">Uçuşlarım</h1>
          <p className="text-gray-400 text-sm mt-0.5">Aktif biletleriniz</p>
        </div>
        <button
          onClick={refetch}
          className="text-gray-400 hover:text-white transition-colors p-1.5 rounded hover:bg-gray-700"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </button>
      </div>

      {error && (
        <div className="mb-6 px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
          {error}
        </div>
      )}

      {loading ? (
        <div className="flex flex-col gap-4">
          <SkeletonCard />
          <SkeletonCard />
        </div>
      ) : flights.length === 0 ? (
        <div className="eco-card text-center py-12">
          <div className="text-5xl mb-4">✈️</div>
          <p className="text-gray-400">Aktif biletiniz bulunamadı.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {flights.map(flight => (
            <FlightCard
              key={flight.flightId}
              flightId={flight.flightId}
              flightCode={flight.flightCode}
              airline={flight.airline}
              iataCode={flight.iataCode}
              destination={flight.destination}
              origin={flight.origin}
              departureTime={flight.departureTime}
              minutesToDeparture={flight.minutesToDeparture}
              gateZoneName={flight.gateZoneName}
              gateDensityLevel={flight.gateDensityLevel}
              colorCode={flight.colorCode}
              seatNumber={flight.seatNumber}
              flightClass={flight.flightClass}
              status={flight.status}
            />
          ))}
        </div>
      )}
    </div>
  )
}
