import api, { setAuthToken } from "./api";

export interface RegisterPayload {
  username: string;
  password: string;
  email?: string;
  fullName?: string;
  department?: string;
  studentId?: string;
  phone?: string;
}

export interface AuthResponse {
  username: string;
  status: string;
  token: string;
  roles: string[];
}

export const registerUser = async (payload: RegisterPayload): Promise<AuthResponse> => {
  const response = await api.post("/auth/register", payload);
  return response.data;
};

export interface LoginPayload {
  username: string;
  password: string;
}

export const loginUser = async (payload: LoginPayload): Promise<AuthResponse> => {
  const response = await api.post("/auth/login", payload);
  if (response.data?.token) {
    setAuthToken(response.data.token);
  }
  return response.data;
};

export const validateRegister = async (username?: string, studentId?: string) => {
  const response = await api.get("/auth/validate", {
    params: { username, studentId },
  });
  return response.data as { usernameAvailable: boolean; studentIdAvailable: boolean };
};
