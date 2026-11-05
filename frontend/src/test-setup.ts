import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
import {configure} from '@testing-library/react';
// React Query notifications cross event-loop turns; retain a finite wait on loaded Windows hosts.
// This affects DOM observation only, never application request/security deadlines or assertions.
configure({asyncUtilTimeout:5000});
afterEach(cleanup);
