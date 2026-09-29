"use client";
const A="ailib.access",R="ailib.refresh";
export const auth={
 access:()=>typeof window==="undefined"?null:localStorage.getItem(A), refresh:()=>typeof window==="undefined"?null:localStorage.getItem(R),
 save:(accessToken:string,refreshToken:string)=>{localStorage.setItem(A,accessToken);localStorage.setItem(R,refreshToken)},
 clear:()=>{localStorage.removeItem(A);localStorage.removeItem(R)}
};
