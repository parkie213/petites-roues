import { createContext, useContext } from "react";
import { languageName } from "./languages.js";

const StudyLanguages = createContext({});

/** Supplies the document's language pair to all player surfaces, including fullscreen overlays. */
export function StudyLanguagesProvider({ document, children }) {
  return (
    <StudyLanguages.Provider value={document}>
      {children}
    </StudyLanguages.Provider>
  );
}

export function useStudyLanguages() {
  const { sourceLanguage, translationLanguage, notesLanguage } =
    useContext(StudyLanguages);
  return {
    sourceLanguage,
    translationLanguage,
    notesLanguage,
    sourceName: languageName(sourceLanguage),
    translationName: languageName(translationLanguage),
  };
}
