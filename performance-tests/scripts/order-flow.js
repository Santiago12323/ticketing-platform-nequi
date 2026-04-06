import http from 'k6/http';
import { check, sleep } from 'k6';
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";

export const options = {
  stages: [
    { duration: '30s', target: 50 },  // Subida rápida a 50
    { duration: '1m', target: 100 }, // Pico de 100 usuarios concurrentes
    { duration: '1m', target: 100 },
    { duration: '30s', target: 0 },
  ],
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  // --- 1. CREATE ORDER  ---
  const createPayload = JSON.stringify({
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "eventId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "totalPrice": 150.0,
    "currency": "USD",
    "seatIds": ["b137232c-34ae-4643-ab5d-b45b7ba9c5e4"]
  });

  const createRes = http.post(`${BASE_URL}/orders`, createPayload, params);

  check(createRes, {
    'Create: status is 202 or 409': (r) => r.status === 202 || r.status === 409,
    'Create: has orderId': (r) => r.json().orderId !== undefined || r.status === 409,
  });

  if (createRes.status === 202) {
    const orderId = createRes.json().orderId;
    sleep(0.5);

    const getRes = http.get(`${BASE_URL}/orders/${orderId}`, params);
    check(getRes, { 'GetStatus: status is 200': (r) => r.status === 200 });

    sleep(0.5);

    const payPayload = JSON.stringify({
      "userId": "123e4567-e89b-12d3-a456-426614174000",
      "eventId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
      "amount": 150.0,
      "currency": "USD",
      "orderId": orderId,
      "seatIds": ["b137232c-34ae-4643-ab5d-b45b7ba9c5e4"]
    });

    const payRes = http.post(`${BASE_URL}/orders/confirm`, payPayload, params);
    check(payRes, { 'Pay: status is 200': (r) => r.status === 200 });
  }

  sleep(1);
}

export function handleSummary(data) {
  return {
    "performance-tests/summary_stress_100.html": htmlReport(data),
  };
}