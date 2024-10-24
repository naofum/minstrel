//
//            _           _            _ 
//           (_)         | |          | |
//  _ __ ___  _ _ __  ___| |_ _ __ ___| |
// | '_ ` _ \| | '_ \/ __| __| '__/ _ \ |
// | | | | | | | | | \__ \ |_| | |  __/ |
// |_| |_| |_|_|_| |_|___/\__|_|  \___|_|
//
// Author:      Alberto Pettarin (www.albertopettarin.it)
// Copyright:   Copyright 2013-2015, ReadBeyond Srl (www.readbeyond.it)
// License:     MIT
// Email:       minstrel@readbeyond.it
// Web:         http://www.readbeyond.it/minstrel/
// Status:      Production
//

#import <Foundation/Foundation.h>
#import "JSONPrintable.h"

@interface ZipAsset : NSObject<JSONPrintable>

@property NSString              *path;
@property NSMutableDictionary   *metadata;

- (id)init:(NSString *)path medatada:(NSMutableDictionary *)metadata;
- (NSComparisonResult)compare:(ZipAsset *)other;
- (NSString *)toString;

@end
