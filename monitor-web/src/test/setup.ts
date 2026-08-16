import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
import '@testing-library/jest-dom/vitest';

// Sin esto, el DOM de un render() sobrevive al siguiente test del mismo
// archivo -- invisible mientras cada test consulta texto único, pero
// CalibracionPanelTest lo destapó: "Guardar calibración" se repite en cada
// test, y sin cleanup(), getByRole encuentra un botón por cada render previo.
afterEach(() => {
  cleanup();
});
