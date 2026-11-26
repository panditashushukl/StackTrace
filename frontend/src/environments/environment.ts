const useLocalBackend = false;

const LOCAL_BACKEND_URL = 'http://localhost:8080';
const RENDER_BACKEND_URL = 'https://stacktrace-h5d3.onrender.com';

const backendUrl = useLocalBackend ? LOCAL_BACKEND_URL : RENDER_BACKEND_URL;

export const environment = {
  production: false,
  useLocalBackend,
  apiUrl: `${backendUrl}/api/v1`,
  googleAuthUrl: `${backendUrl}/oauth2/authorization/google`,
};
