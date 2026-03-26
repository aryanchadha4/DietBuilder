export function cmToFtIn(cm: number): { feet: number; inches: number } {
  const totalInches = cm / 2.54;
  const feet = Math.floor(totalInches / 12);
  const inches = Math.round(totalInches % 12);
  return { feet, inches: inches === 12 ? 0 : inches };
}

export function ftInToCm(feet: number, inches: number): number {
  return (feet * 12 + inches) * 2.54;
}

export function kgToLbs(kg: number): number {
  return kg / 0.453592;
}

export function lbsToKg(lbs: number): number {
  return lbs * 0.453592;
}

export function formatHeight(cm: number, preferredUnits?: string): string {
  if (!cm || cm <= 0) return "—";
  if (preferredUnits === "IMPERIAL") {
    const { feet, inches } = cmToFtIn(cm);
    return `${feet}'${inches}"`;
  }
  return `${Math.round(cm)} cm`;
}

export function formatWeight(kg: number, preferredUnits?: string): string {
  if (!kg || kg <= 0) return "—";
  if (preferredUnits === "IMPERIAL") {
    return `${Math.round(kgToLbs(kg))} lbs`;
  }
  return `${Math.round(kg * 10) / 10} kg`;
}
