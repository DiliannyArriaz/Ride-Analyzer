import React, { useState, useRef, useEffect } from 'react';
import { Car, Zap, Clock, Navigation, TrendingUp, X, Maximize2, Minimize2 } from 'lucide-react';

export default function CompactDraggableBubble() {
  const [isProfitable, setIsProfitable] = useState(true);
  const [isExpanded, setIsExpanded] = useState(false);
  const [position, setPosition] = useState({ x: 20, y: 100 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const bubbleRef = useRef(null);
  
  // Simular datos del viaje
  const tripData = {
    platform: 'Uber',
    value: 3564,
    time: 13,
    distance: 5.8,
    perMinute: 274,
    perKm: 614
  };

  const toggleProfitable = () => setIsProfitable(!isProfitable);

  // Manejo del drag
  const handleMouseDown = (e) => {
    if (e.target.closest('.no-drag')) return;
    setIsDragging(true);
    setDragStart({
      x: e.clientX - position.x,
      y: e.clientY - position.y
    });
  };

  const handleTouchStart = (e) => {
    if (e.target.closest('.no-drag')) return;
    setIsDragging(true);
    const touch = e.touches[0];
    setDragStart({
      x: touch.clientX - position.x,
      y: touch.clientY - position.y
    });
  };

  const handleMouseMove = (e) => {
    if (!isDragging) return;
    setPosition({
      x: e.clientX - dragStart.x,
      y: e.clientY - dragStart.y
    });
  };

  const handleTouchMove = (e) => {
    if (!isDragging) return;
    const touch = e.touches[0];
    setPosition({
      x: touch.clientX - dragStart.x,
      y: touch.clientY - dragStart.y
    });
  };

  const handleDragEnd = () => {
    setIsDragging(false);
  };

  useEffect(() => {
    if (isDragging) {
      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', handleDragEnd);
      window.addEventListener('touchmove', handleTouchMove);
      window.addEventListener('touchend', handleDragEnd);
      
      return () => {
        window.removeEventListener('mousemove', handleMouseMove);
        window.removeEventListener('mouseup', handleDragEnd);
        window.removeEventListener('touchmove', handleTouchMove);
        window.removeEventListener('touchend', handleDragEnd);
      };
    }
  }, [isDragging, dragStart]);

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-900 via-purple-900 to-gray-900 p-8 relative overflow-hidden">
      {/* Fondo simulado */}
      <div className="max-w-md mx-auto bg-gray-800 rounded-3xl shadow-2xl p-6 border border-purple-500/30">
        <div className="text-center mb-4">
          <div className="w-16 h-16 bg-gradient-to-br from-cyan-500 to-purple-500 rounded-2xl mx-auto mb-4 flex items-center justify-center shadow-lg shadow-cyan-500/50">
            <Car className="w-10 h-10 text-white" />
          </div>
          <h2 className="text-2xl font-black text-white mb-2">Simulación de Pantalla</h2>
          <p className="text-purple-300 text-sm font-bold">Arrastra la burbuja para moverla 👆</p>
        </div>
        
        <div className="space-y-3">
          <button
            onClick={toggleProfitable}
            className="w-full bg-gradient-to-r from-cyan-500 to-purple-500 text-white py-3 rounded-xl font-black shadow-lg shadow-cyan-500/50"
          >
            Cambiar: {isProfitable ? 'Rentable' : 'No Rentable'}
          </button>
          
          <button
            onClick={() => setIsExpanded(!isExpanded)}
            className="w-full bg-gray-700 text-white py-3 rounded-xl font-bold"
          >
            {isExpanded ? 'Modo Compacto' : 'Modo Expandido'}
          </button>
        </div>
      </div>

      {/* BURBUJA FLOTANTE COMPACTA Y ARRASTRABLE */}
      <div 
        ref={bubbleRef}
        className={`fixed select-none transition-shadow ${isDragging ? 'cursor-grabbing' : 'cursor-grab'}`}
        style={{ 
          left: `${position.x}px`, 
          top: `${position.y}px`,
          maxWidth: isExpanded ? '240px' : '200px',
          zIndex: 9999,
          touchAction: 'none'
        }}
        onMouseDown={handleMouseDown}
        onTouchStart={handleTouchStart}
      >
        <div className={`rounded-2xl overflow-hidden backdrop-blur-xl shadow-2xl ${
          isProfitable 
            ? 'bg-gray-900/95 border-2 border-cyan-400 shadow-cyan-500/50' 
            : 'bg-gray-900/95 border-2 border-red-400 shadow-red-500/50'
        }`}>
          {/* Header compacto */}
          <div className={`px-2.5 py-1.5 flex items-center justify-between ${
            isProfitable 
              ? 'bg-gradient-to-r from-cyan-500/20 to-purple-500/20' 
              : 'bg-gradient-to-r from-red-500/20 to-pink-500/20'
          }`}>
            <div className="flex items-center gap-1.5">
              <div className={`px-1.5 py-0.5 rounded font-black text-xs ${
                isProfitable ? 'bg-cyan-400 text-gray-900' : 'bg-red-400 text-gray-900'
              }`}>
                {tripData.platform}
              </div>
              <Zap className={`w-3 h-3 ${isProfitable ? 'text-cyan-400' : 'text-red-400'}`} />
            </div>
            <button 
              onClick={() => setIsExpanded(!isExpanded)}
              className="no-drag p-1 hover:bg-white/10 rounded"
            >
              {isExpanded ? (
                <Minimize2 className="w-3.5 h-3.5 text-gray-400" />
              ) : (
                <Maximize2 className="w-3.5 h-3.5 text-gray-400" />
              )}
            </button>
          </div>

          {/* Body compacto */}
          <div className="p-2 space-y-1.5">
            {/* Estado y valor principal */}
            <div className={`rounded-lg p-2 text-center ${
              isProfitable
                ? 'bg-gradient-to-br from-cyan-500/30 to-purple-500/30 border border-cyan-400/50'
                : 'bg-gradient-to-br from-red-500/30 to-pink-500/30 border border-red-400/50'
            }`}>
              <p className={`text-xs font-black mb-0.5 ${
                isProfitable ? 'text-cyan-400' : 'text-red-400'
              }`}>
                {isProfitable ? '✓ RENTABLE' : '✕ NO RENTABLE'}
              </p>
              <p className={`text-2xl font-black ${
                isProfitable ? 'text-cyan-400' : 'text-red-400'
              }`}>
                ${tripData.value.toLocaleString()}
              </p>
            </div>

            {/* Datos en grid compacto */}
            {isExpanded ? (
              <div className="grid grid-cols-2 gap-1.5 text-xs">
                <div className="bg-gray-800/50 backdrop-blur rounded-lg p-1.5 border border-gray-700">
                  <div className="flex items-center gap-0.5 mb-0.5">
                    <Clock className="w-2.5 h-2.5 text-blue-400" />
                    <p className="text-blue-400 font-bold text-xs">Tiempo</p>
                  </div>
                  <p className="text-white font-black text-xs">{tripData.time} min</p>
                </div>
                <div className="bg-gray-800/50 backdrop-blur rounded-lg p-1.5 border border-gray-700">
                  <div className="flex items-center gap-0.5 mb-0.5">
                    <Navigation className="w-2.5 h-2.5 text-purple-400" />
                    <p className="text-purple-400 font-bold text-xs">Dist.</p>
                  </div>
                  <p className="text-white font-black text-xs">{tripData.distance} km</p>
                </div>
                <div className="bg-gray-800/50 backdrop-blur rounded-lg p-1.5 border border-gray-700">
                  <div className="flex items-center gap-0.5 mb-0.5">
                    <TrendingUp className="w-2.5 h-2.5 text-green-400" />
                    <p className="text-green-400 font-bold text-xs">$/min</p>
                  </div>
                  <p className="text-white font-black text-xs">${tripData.perMinute}</p>
                </div>
                <div className="bg-gray-800/50 backdrop-blur rounded-lg p-1.5 border border-gray-700">
                  <div className="flex items-center gap-0.5 mb-0.5">
                    <TrendingUp className="w-2.5 h-2.5 text-pink-400" />
                    <p className="text-pink-400 font-bold text-xs">$/km</p>
                  </div>
                  <p className="text-white font-black text-xs">${tripData.perKm}</p>
                </div>
              </div>
            ) : (
              // Modo ultra compacto - solo info esencial
              <div className="flex items-center justify-between text-xs">
                <div className="flex items-center gap-1">
                  <Clock className="w-3 h-3 text-blue-400" />
                  <span className="text-white font-bold">{tripData.time}m</span>
                </div>
                <div className="flex items-center gap-1">
                  <Navigation className="w-3 h-3 text-purple-400" />
                  <span className="text-white font-bold">{tripData.distance}km</span>
                </div>
                <div className="flex items-center gap-1">
                  <TrendingUp className="w-3 h-3 text-green-400" />
                  <span className="text-white font-bold">${tripData.perMinute}</span>
                </div>
              </div>
            )}

            {/* Botón de acción compacto */}
            <button 
              className={`no-drag w-full py-2 rounded-lg font-black text-xs transition-all transform active:scale-95 ${
                isProfitable
                  ? 'bg-gradient-to-r from-cyan-500 to-purple-500 text-white shadow-lg shadow-cyan-500/50'
                  : 'bg-gradient-to-r from-red-500 to-pink-500 text-white shadow-lg shadow-red-500/50'
              }`}
            >
              {isProfitable ? '✓ Aceptar' : '✕ Rechazar'}
            </button>
          </div>
        </div>

        {/* Indicador de arrastre */}
        <div className="mt-1 text-center">
          <div className="inline-flex items-center gap-1 bg-gray-900/80 backdrop-blur px-2 py-1 rounded-full border border-gray-700">
            <div className="flex gap-0.5">
              <div className="w-1 h-1 rounded-full bg-cyan-400"></div>
              <div className="w-1 h-1 rounded-full bg-cyan-400"></div>
              <div className="w-1 h-1 rounded-full bg-cyan-400"></div>
            </div>
          </div>
        </div>
      </div>

      {/* Indicador de tamaño */}
      <div className="fixed bottom-8 left-1/2 transform -translate-x-1/2 bg-gradient-to-r from-cyan-500 to-purple-500 text-white px-4 py-2 rounded-full text-xs font-black shadow-lg shadow-cyan-500/50">
        {isExpanded ? 'Expandido: 240px × ~200px' : 'Compacto: 200px × ~150px'} • Arrastrable
      </div>
    </div>
  );
}