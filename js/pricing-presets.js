// ==============================================================================
// COPIADORA GRAFIPLOT - CATÁLOGO DE PRECIOS Y SERVICIOS PREDEFINIDOS
// ==============================================================================
// Este catálogo es configurable en cliente (archivo provisional embebido).
// Más adelante podrá integrarse directamente con el sistema de notas y SQL.

const PRESET_SERVICES = [
  {
    id: 'a4-1cara',
    category: 'Copias e Impresiones A4',
    name: 'Copias / Impresiones A4 (1 Cara - B/N o Color)',
    suggestedPrices: [0.07, 0.08, 0.09, 0.10],
    defaultPrice: 0.10,
    unit: 'hoja'
  },
  {
    id: 'a4-duplex',
    category: 'Copias e Impresiones A4',
    name: 'Copias / Impresiones A4 (Doble Cara / Duplex)',
    suggestedPrices: [0.08, 0.09, 0.10, 0.12, 0.15],
    defaultPrice: 0.15,
    unit: 'hoja'
  },
  {
    id: 'a3-1cara',
    category: 'Impresiones A3',
    name: 'Impresión A3 Bond normal (1 Cara - B/N o Color)',
    suggestedPrices: [0.50],
    defaultPrice: 0.50,
    unit: 'hoja'
  },
  {
    id: 'a3-duplex',
    category: 'Impresiones A3',
    name: 'Impresión A3 Bond normal (Duplex / 2 Caras)',
    suggestedPrices: [0.50, 0.60, 0.70],
    defaultPrice: 0.70,
    unit: 'hoja'
  },
  {
    id: 'a3-cartulina',
    category: 'Impresiones A3',
    name: 'Impresión A3 en Cartulina',
    suggestedPrices: [1.50],
    defaultPrice: 1.50,
    unit: 'hoja'
  },
  {
    id: 'a3-adhesivo',
    category: 'Impresiones A3',
    name: 'Impresión A3 en Papel Adhesivo',
    suggestedPrices: [5.00],
    defaultPrice: 5.00,
    unit: 'hoja'
  },
  {
    id: 'a4-cartulina-escolar',
    category: 'Papeles Especiales A4',
    name: 'Impresión A4 en Cartulina Escolar',
    suggestedPrices: [0.50, 0.05],
    defaultPrice: 0.50,
    unit: 'hoja'
  },
  {
    id: 'a4-cartulina-hilo',
    category: 'Papeles Especiales A4',
    name: 'Impresión A4 en Cartulina de Hilo',
    suggestedPrices: [1.00],
    defaultPrice: 1.00,
    unit: 'hoja'
  },
  {
    id: 'a4-fotografico',
    category: 'Papeles Especiales A4',
    name: 'Impresión A4 Fotográfico (Normal o Adhesivo)',
    suggestedPrices: [1.50],
    defaultPrice: 1.50,
    unit: 'hoja'
  },
  {
    id: 'a4-folleto',
    category: 'Folletos y Cuadernillos',
    name: 'Folleto / Cuadernillo A4 (4 págs por hoja)',
    suggestedPrices: [0.08, 0.10, 0.12, 0.15],
    defaultPrice: 0.10,
    isFolleto: true,
    unit: 'hoja'
  },
  {
    id: 'plotter-a2',
    category: 'Planos y Plotter (1 Cara Bond)',
    name: 'Plano A2 Papel Bond normal (1 Cara)',
    suggestedPrices: [1.50],
    defaultPrice: 1.50,
    unit: 'plano'
  },
  {
    id: 'plotter-a1',
    category: 'Planos y Plotter (1 Cara Bond)',
    name: 'Plano A1 Papel Bond normal (1 Cara)',
    suggestedPrices: [2.00],
    defaultPrice: 2.00,
    unit: 'plano'
  },
  {
    id: 'plotter-a0',
    category: 'Planos y Plotter (1 Cara Bond)',
    name: 'Plano A0 Papel Bond normal (1 Cara)',
    suggestedPrices: [4.00],
    defaultPrice: 4.00,
    unit: 'plano'
  },
  {
    id: 'acabado-anillado',
    category: 'Acabados y Otros',
    name: 'Anillado / Espiralado',
    suggestedPrices: [2.00, 3.00, 4.00, 5.00],
    defaultPrice: 3.00,
    unit: 'unidad'
  },
  {
    id: 'acabado-enmicado',
    category: 'Acabados y Otros',
    name: 'Enmicado / Plastificado (Carnet o A4)',
    suggestedPrices: [1.00, 2.00],
    defaultPrice: 2.00,
    unit: 'unidad'
  }
];

/**
 * Función de redondeo al décimo inferior (p. ej. 10.75 -> 10.70, 9.89 -> 9.80).
 */
function roundFloorTenth(val) {
  const num = parseFloat(val);
  if (isNaN(num) || num <= 0) return 0;
  return Math.floor((num + 0.00001) * 10) / 10;
}

if (typeof window !== 'undefined') {
  window.PRESET_SERVICES = PRESET_SERVICES;
  window.roundFloorTenth = roundFloorTenth;
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { PRESET_SERVICES, roundFloorTenth };
}

