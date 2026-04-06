import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const COMMON_HEADERS = {
    'Content-Type': 'application/json',
    'X-Source-System': 'K6-LoadTest',
};

export function validateResponse(res, name, expectedStatus = 200) {
    const isStatusOk = res.status === expectedStatus;
    const hasBody = res.body && res.body.length > 0;

    check(res, {
        [`${name}: status is ${expectedStatus}`]: () => isStatusOk,
        [`${name}: has body`]: () => hasBody,
    });

    if (!isStatusOk) {
        console.warn(`${name} failed! Status: ${res.status}. Body: ${res.body}`);
    }
}