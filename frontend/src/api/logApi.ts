import api from "./api";

export interface ClickLogPayload {
  isbn: string;
  target_channel: string;
  slider_value: number;
}

export const sendClickLog = async (logData: ClickLogPayload) => {
  try {
    const response = await api.post("/logs/click", logData);
    return response.data;
  } catch (error) {
    console.error("Failed to send click log", error);
    throw error;
  }
};
