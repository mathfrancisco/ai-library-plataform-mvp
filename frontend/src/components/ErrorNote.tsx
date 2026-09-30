import {errorMessage} from "@/lib/api";
export function ErrorNote({error}:{error:unknown}){if(!error)return null;return <p role="alert" className="errornote">{errorMessage(error)}</p>}
