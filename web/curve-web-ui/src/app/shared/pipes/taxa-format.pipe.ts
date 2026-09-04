import { Pipe, PipeTransform } from '@angular/core';
import { MarketDataFormatter } from '../../core/formatting/market-data-formatter.ts';

@Pipe({
  name: 'taxaFormat',
  standalone: true
})
export class TaxaFormatPipe implements PipeTransform {
  transform(value: string | null | undefined, incluirPercent: boolean = true): string {
    return MarketDataFormatter.formatarTaxa(value, incluirPercent);
  }
}

@Pipe({
  name: 'fatorDesconto',
  standalone: true
})
export class FatorDescontoPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return MarketDataFormatter.formatarFatorDesconto(value);
  }
}

@Pipe({
  name: 'bpsFormat',
  standalone: true
})
export class BpsFormatPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return MarketDataFormatter.formatarBps(value);
  }
}
