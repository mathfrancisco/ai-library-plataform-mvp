export type Book={id?:string;provider?:string;externalId?:string;isbn13?:string;isbn10?:string;title:string;subtitle?:string;authors:string[];categories?:string[];description?:string;language?:string;publisher?:string;publishedYear?:number;pageCount?:number;coverUrl?:string;publicDomain?:boolean};
export type CatalogPage={items:Book[];total:number};
export type SearchHit={localBookId?:string;provider?:string;externalId?:string;title:string;authors:string[];coverUrl?:string;description?:string;score:number;matchType:string};
export type LibraryStatus="WANT_TO_READ"|"READING"|"READ"|"DROPPED";
export type LibraryItem={book:Book;status:LibraryStatus;favorite:boolean;rating?:number;addedAt:string};
export type DocumentItem={id:string;bookId?:string;originalName:string;contentType:string;sizeBytes:number;status:"STORED"|"PROCESSING"|"READY"|"FAILED";errorMessage?:string;chunkCount:number;createdAt:string};
